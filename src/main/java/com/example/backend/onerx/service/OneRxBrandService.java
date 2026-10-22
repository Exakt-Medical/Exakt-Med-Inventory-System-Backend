package com.example.backend.onerx.service;

import com.example.backend.entity.Facility;
import com.example.backend.entity.LibMedicine;
import com.example.backend.entity.Sku;
import com.example.backend.onerx.dto.OneRxBrandResponseDto;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.LibMedicineRepository;
import com.example.backend.repository.SkuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OneRxBrandService {

    private final SkuRepository skuRepository;
    private final LibMedicineRepository libMedicineRepository;
    private final FacilityRepository facilityRepository;

    public OneRxBrandService(SkuRepository skuRepository,
                             LibMedicineRepository libMedicineRepository,
                             FacilityRepository facilityRepository) {
        this.skuRepository = skuRepository;
        this.libMedicineRepository = libMedicineRepository;
        this.facilityRepository = facilityRepository;
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

        // 2. Resolve Facility
        Facility facility = resolveFacility(trimmedFacilityCode)
                .orElseThrow(() -> new RuntimeException("Facility not found with code: " + trimmedFacilityCode));

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

        // Group SKUs by drugCode
        java.util.Map<String, List<Sku>> skusByDrugCode = availableSkus.stream()
                .filter(sku -> sku.getLibMedicine() != null && sku.getLibMedicine().getDrugCode() != null)
                .collect(Collectors.groupingBy(sku -> sku.getLibMedicine().getDrugCode()));

        String displayFacilityCode = (facility.getFacilityCode() != null && !facility.getFacilityCode().isBlank())
                ? facility.getFacilityCode()
                : String.valueOf(facility.getId());

        // 5. Build response list matching the requested order
        List<OneRxBrandResponseDto> responseList = new java.util.ArrayList<>();
        for (String code : distinctCodes) {
            LibMedicine medicine = medicineMap.get(code);
            List<Sku> skusForDrug = skusByDrugCode.getOrDefault(code, java.util.Collections.emptyList());

            List<OneRxBrandResponseDto.SkuBrandItemDto> skuItems = skusForDrug.stream()
                    .map(sku -> OneRxBrandResponseDto.SkuBrandItemDto.builder()
                            .name(sku.getName())
                            .brandName(sku.getBrandName())
                            .quantity(sku.getUnits() != null ? sku.getUnits() : 0L)
                            .packagingUnit(sku.getPackagingUnit())
                            .dosageForm(sku.getDosageForm())
                            .dosageStrength(sku.getDosageStrength())
                            .build())
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
