package com.example.backend.onerx.service;

import com.example.backend.entity.Facility;
import com.example.backend.entity.LibMedicine;
import com.example.backend.entity.Sku;
import com.example.backend.onerx.dto.OneRxBrandResponseDto;
import com.example.backend.repository.BatchRepository;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.LibMedicineRepository;
import com.example.backend.repository.SkuRepository;
import com.example.backend.security.FacilityAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OneRxBrandService {

    private final SkuRepository skuRepository;
    private final BatchRepository batchRepository;
    private final LibMedicineRepository libMedicineRepository;
    private final FacilityRepository facilityRepository;
    private final FacilityAccessGuard facilityAccessGuard;

    public OneRxBrandService(SkuRepository skuRepository,
                             BatchRepository batchRepository,
                             LibMedicineRepository libMedicineRepository,
                             FacilityRepository facilityRepository,
                             FacilityAccessGuard facilityAccessGuard) {
        this.skuRepository = skuRepository;
        this.batchRepository = batchRepository;
        this.libMedicineRepository = libMedicineRepository;
        this.facilityRepository = facilityRepository;
        this.facilityAccessGuard = facilityAccessGuard;
    }

    /**
     * Resolves available brand SKUs for a single drug code.
     */
    @Transactional(readOnly = true)
    public OneRxBrandResponseDto getAvailableBrands(String drugCode, String facilityCode) {
        if (drugCode == null || drugCode.trim().isBlank()) {
            throw new IllegalArgumentException("Parameter 'drug_code' is required.");
        }
        List<OneRxBrandResponseDto> results = getAvailableBrandsForMultipleDrugs(List.of(drugCode), facilityCode);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Resolves available brand SKUs for comma-separated drug codes.
     */
    @Transactional(readOnly = true)
    public List<OneRxBrandResponseDto> getAvailableBrandsForCommaSeparatedDrugs(String drugCodeParam, String facilityCode) {
        if (drugCodeParam == null || drugCodeParam.trim().isBlank()) {
            throw new IllegalArgumentException("Parameter 'drug_code' is required.");
        }
        return getAvailableBrandsForMultipleDrugs(List.of(drugCodeParam), facilityCode);
    }

    /**
     * Resolves available brand SKUs for multiple generic drug codes in a facility.
     *
     * @param drugCodes    list or comma-separated list of drug codes (e.g. ["DRG-001", "DRG-002"])
     * @param facilityCode the facility code (e.g. "FAC-001")
     * @return List of OneRxBrandResponseDto, one entry per requested drug code
     */
    @Transactional(readOnly = true)
    public List<OneRxBrandResponseDto> getAvailableBrandsForMultipleDrugs(List<String> drugCodes, String facilityCode) {
        if (facilityCode == null || facilityCode.trim().isBlank()) {
            throw new IllegalArgumentException("Parameter 'facility_code' is required.");
        }

        // 1. Parse, clean, split comma-separated codes, and deduplicate preserving order
        java.util.Set<String> distinctCodes = new java.util.LinkedHashSet<>();
        if (drugCodes != null) {
            for (String rawCode : drugCodes) {
                if (rawCode != null) {
                    for (String part : rawCode.split(",")) {
                        String trimmed = part.trim();
                        if (!trimmed.isBlank()) {
                            distinctCodes.add(trimmed);
                        }
                    }
                }
            }
        }

        if (distinctCodes.isEmpty()) {
            throw new IllegalArgumentException("At least one valid 'drug_code' is required.");
        }

        String trimmedFacilityCode = facilityCode.trim();

        // 2. Resolve Facility & enforce facility access
        Facility facility = resolveFacility(trimmedFacilityCode)
                .orElseThrow(() -> new RuntimeException("Facility not found with code: " + trimmedFacilityCode));

        facilityAccessGuard.verifyFacilityAccess(facility.getId());

        // 3. Resolve all LibMedicines
        List<LibMedicine> medicines = libMedicineRepository.findByDrugCodeIn(distinctCodes);
        java.util.Map<String, LibMedicine> medicineMap = medicines.stream()
                .collect(Collectors.toMap(LibMedicine::getDrugCode, m -> m, (a, b) -> a));

        // Validate that requested drug codes exist in the medicine library
        for (String code : distinctCodes) {
            if (!medicineMap.containsKey(code)) {
                throw new RuntimeException("Drug code not found in medicine library: " + code);
            }
        }

        // 4. Query all available SKUs in the facility for these drugs (units > 0)
        List<Sku> availableSkus = skuRepository.findAvailableSkusByDrugCodesAndFacilityId(
                distinctCodes,
                facility.getId()
        );

        // Compute un-deducted expired batch units per SKU so expired stock is excluded from available quantity
        Map<Long, Long> undeductedExpiredUnitsBySkuId = new HashMap<>();
        List<Object[]> expiredMetrics = batchRepository.findExpiredBatchMetricsByFacilityId(
                facility.getId(),
                LocalDate.now()
        );
        for (Object[] row : expiredMetrics) {
            if (row != null && row.length >= 3 && row[0] != null && row[2] != null) {
                Long skuId = ((Number) row[0]).longValue();
                Long expiredUnits = ((Number) row[2]).longValue();
                undeductedExpiredUnitsBySkuId.put(skuId, expiredUnits);
            }
        }

        // Group SKUs by drugCode
        java.util.Map<String, List<Sku>> skusByDrugCode = availableSkus.stream()
                .filter(sku -> sku.getLibMedicine() != null && sku.getLibMedicine().getDrugCode() != null)
                .collect(Collectors.groupingBy(sku -> sku.getLibMedicine().getDrugCode()));

        String displayFacilityCode = (facility.getFacilityCode() != null && !facility.getFacilityCode().isBlank())
                ? facility.getFacilityCode()
                : String.valueOf(facility.getId());

        // 5. Build response list matching the requested order (excluding un-deducted expired batch units)
        List<OneRxBrandResponseDto> responseList = new java.util.ArrayList<>();
        for (String code : distinctCodes) {
            LibMedicine medicine = medicineMap.get(code);
            List<Sku> skusForDrug = skusByDrugCode.getOrDefault(code, java.util.Collections.emptyList());

            List<OneRxBrandResponseDto.SkuBrandItemDto> skuItems = skusForDrug.stream()
                    .map(sku -> {
                        long rawUnits = sku.getUnits() != null ? sku.getUnits() : 0L;
                        long expiredUnits = undeductedExpiredUnitsBySkuId.getOrDefault(sku.getId(), 0L);
                        long effectiveQuantity = Math.max(0L, rawUnits - expiredUnits);
                        return OneRxBrandResponseDto.SkuBrandItemDto.builder()
                                .name(sku.getName())
                                .brandName(sku.getBrandName())
                                .quantity(effectiveQuantity)
                                .packagingUnit(sku.getPackagingUnit())
                                .dosageForm(sku.getDosageForm())
                                .dosageStrength(sku.getDosageStrength())
                                .build();
                    })
                    .filter(item -> item.getQuantity() != null && item.getQuantity() > 0)
                    .collect(Collectors.toList());

            responseList.add(OneRxBrandResponseDto.builder()
                    .drugCode(medicine.getDrugCode())
                    .drugName(medicine.getDrugDescription())
                    .facilityCode(displayFacilityCode)
                    .sku(skuItems)
                    .build());
        }

        return responseList;
    }

    private Optional<Facility> resolveFacility(String facilityCode) {
        Optional<Facility> opt = facilityRepository.findByFacilityCode(facilityCode);
        if (opt.isPresent()) {
            return opt;
        }
        try {
            Long numericId = Long.parseLong(facilityCode);
            return facilityRepository.findById(numericId);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
