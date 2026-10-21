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
     * Resolves available brand SKUs for OneRx integration.
     *
     * @param drugCode     the generic drug code (e.g. "DRG-000123")
     * @param facilityCode the facility code (e.g. "FAC-001")
     * @return OneRxBrandResponseDto containing drug details and available SKU stock
     */
    @Transactional(readOnly = true)
    public OneRxBrandResponseDto getAvailableBrands(String drugCode, String facilityCode) {
        if (drugCode == null || drugCode.trim().isBlank()) {
            throw new IllegalArgumentException("Parameter 'drug_code' is required.");
        }
        if (facilityCode == null || facilityCode.trim().isBlank()) {
            throw new IllegalArgumentException("Parameter 'facility_code' is required.");
        }

        String trimmedDrugCode = drugCode.trim();
        String trimmedFacilityCode = facilityCode.trim();

        // 1. Resolve Facility (by facilityCode first, then numeric ID fallback)
        Facility facility = resolveFacility(trimmedFacilityCode)
                .orElseThrow(() -> new RuntimeException("Facility not found with code: " + trimmedFacilityCode));

        // 2. Resolve LibMedicine
        LibMedicine medicine = libMedicineRepository.findByDrugCode(trimmedDrugCode)
                .orElseThrow(() -> new RuntimeException("Drug code not found in medicine library: " + trimmedDrugCode));

        // 3. Query all available SKUs in the facility for this drug (units > 0)
        List<Sku> availableSkus = skuRepository.findAvailableSkusByDrugCodeAndFacilityId(
                medicine.getDrugCode(),
                facility.getId()
        );

        // 4. Map into OneRx response structure
        List<OneRxBrandResponseDto.SkuBrandItemDto> skuItems = availableSkus.stream()
                .map(sku -> OneRxBrandResponseDto.SkuBrandItemDto.builder()
                        .name(sku.getName())
                        .brandName(sku.getBrandName())
                        .quantity(sku.getUnits() != null ? sku.getUnits() : 0L)
                        .packagingUnit(sku.getPackagingUnit())
                        .dosageForm(sku.getDosageForm())
                        .dosageStrength(sku.getDosageStrength())
                        .build())
                .collect(Collectors.toList());

        String displayFacilityCode = (facility.getFacilityCode() != null && !facility.getFacilityCode().isBlank())
                ? facility.getFacilityCode()
                : String.valueOf(facility.getId());

        return OneRxBrandResponseDto.builder()
                .drugCode(medicine.getDrugCode())
                .drugName(medicine.getDrugDescription())
                .facilityCode(displayFacilityCode)
                .sku(skuItems)
                .build();
    }

    private Optional<Facility> resolveFacility(String facilityCode) {
        return facilityRepository.findByFacilityCode(facilityCode);
    }
}
