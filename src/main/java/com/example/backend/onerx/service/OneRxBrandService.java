package com.example.backend.onerx.service;

import com.example.backend.entity.LibMedicine;
import com.example.backend.entity.Pharmacy;
import com.example.backend.entity.Sku;
import com.example.backend.onerx.dto.OneRxBrandResponseDto;
import com.example.backend.repository.BatchRepository;
import com.example.backend.repository.LibMedicineRepository;
import com.example.backend.repository.PharmacyRepository;
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
    private final PharmacyRepository pharmacyRepository;
    private final FacilityAccessGuard facilityAccessGuard;

    public OneRxBrandService(SkuRepository skuRepository,
                             BatchRepository batchRepository,
                             LibMedicineRepository libMedicineRepository,
                             PharmacyRepository pharmacyRepository,
                             FacilityAccessGuard facilityAccessGuard) {
        this.skuRepository = skuRepository;
        this.batchRepository = batchRepository;
        this.libMedicineRepository = libMedicineRepository;
        this.pharmacyRepository = pharmacyRepository;
        this.facilityAccessGuard = facilityAccessGuard;
    }

    /**
     * Resolves available brand SKUs for a single drug code.
     */
    @Transactional(readOnly = true)
    public OneRxBrandResponseDto getAvailableBrands(String drugCode, String pharmacyCode) {
        if (drugCode == null || drugCode.trim().isBlank()) {
            throw new IllegalArgumentException("Parameter 'drug_code' is required.");
        }
        List<OneRxBrandResponseDto> results = getAvailableBrandsForMultipleDrugs(List.of(drugCode), pharmacyCode);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Resolves available brand SKUs for comma-separated drug codes.
     */
    @Transactional(readOnly = true)
    public List<OneRxBrandResponseDto> getAvailableBrandsForCommaSeparatedDrugs(String drugCodeParam, String pharmacyCode) {
        if (drugCodeParam == null || drugCodeParam.trim().isBlank()) {
            throw new IllegalArgumentException("Parameter 'drug_code' is required.");
        }
        return getAvailableBrandsForMultipleDrugs(List.of(drugCodeParam), pharmacyCode);
    }

    /**
     * Resolves available brand SKUs for multiple generic drug codes in a pharmacy.
     *
     * @param drugCodes    list or comma-separated list of drug codes (e.g. ["DRG-001", "DRG-002"])
     * @param pharmacyCode the pharmacy code (e.g. "PHM-001")
     * @return List of OneRxBrandResponseDto, one entry per requested drug code
     */
    @Transactional(readOnly = true)
    public List<OneRxBrandResponseDto> getAvailableBrandsForMultipleDrugs(List<String> drugCodes, String pharmacyCode) {
        if (pharmacyCode == null || pharmacyCode.trim().isBlank()) {
            throw new IllegalArgumentException("Parameter 'pharmacy_code' is required.");
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

        String trimmedPharmacyCode = pharmacyCode.trim();

        // 2. Resolve Pharmacy & enforce pharmacy access
        Pharmacy pharmacy = resolvePharmacy(trimmedPharmacyCode)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found with code: " + trimmedPharmacyCode));

        facilityAccessGuard.verifyPharmacyAccess(pharmacy.getId());

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

        // 4. Query all available SKUs in the pharmacy for these drugs (units > 0)
        List<Sku> availableSkus = skuRepository.findAvailableSkusByDrugCodesAndPharmacyId(
                distinctCodes,
                pharmacy.getId()
        );

        // Compute un-deducted expired batch units per SKU so expired stock is excluded from available quantity
        Map<Long, Long> undeductedExpiredUnitsBySkuId = new HashMap<>();
        List<Object[]> expiredMetrics = batchRepository.findExpiredBatchMetricsByPharmacyId(
                pharmacy.getId(),
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

        String displayPharmacyCode = (pharmacy.getPharmacyCode() != null && !pharmacy.getPharmacyCode().isBlank())
                ? pharmacy.getPharmacyCode()
                : String.valueOf(pharmacy.getId());

        String displayFacilityCode = (pharmacy.getFacility() != null && pharmacy.getFacility().getFacilityCode() != null)
                ? pharmacy.getFacility().getFacilityCode()
                : null;

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
                    .pharmacyCode(displayPharmacyCode)
                    .facilityCode(displayFacilityCode)
                    .sku(skuItems)
                    .build());
        }

        return responseList;
    }

    private Optional<Pharmacy> resolvePharmacy(String pharmacyCode) {
        Optional<Pharmacy> opt = pharmacyRepository.findByPharmacyCode(pharmacyCode);
        if (opt.isPresent()) {
            return opt;
        }
        try {
            Long numericId = Long.parseLong(pharmacyCode);
            return pharmacyRepository.findById(numericId);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
