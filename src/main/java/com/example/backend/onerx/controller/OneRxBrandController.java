package com.example.backend.onerx.controller;

import com.example.backend.onerx.dto.OneRxBrandResponseDto;
import com.example.backend.onerx.service.OneRxBrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/brands")
@Tag(name = "OneRx Integration - Brand Availability", description = "Endpoints for OneRx system integration to query available medication brands and SKU quantities by pharmacy.")
public class OneRxBrandController {

    private final OneRxBrandService oneRxBrandService;

    public OneRxBrandController(OneRxBrandService oneRxBrandService) {
        this.oneRxBrandService = oneRxBrandService;
    }

    /**
     * GET /api/brands?drug_code=DRG-000123&pharmacy_code=PHM-001
     * GET /api/brands?drug_code=DRG-000123,DRG-000456&pharmacy_code=PHM-001
     *
     * Headers:
     * x-api-key: <key> OR Authorization: Bearer <token>
     * Accept: application/json
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get available brand SKUs for generic drug(s) in a pharmacy for OneRx",
            description = "Allows the OneRx 3rd-party system to query current in-stock brands and SKU packaging units for comma-separated drug codes within a specific pharmacy."
    )
    public ResponseEntity<List<OneRxBrandResponseDto>> getAvailableBrands(
            @Parameter(
                    description = "Generic Drug Code(s), comma-separated (e.g. DRG-000123 or DRG-000123,DRG-000456)",
                    example = "DRG-000123,DRG-000456",
                    required = true
            )
            @RequestParam("drug_code") String drugCode,

            @Parameter(
                    description = "Pharmacy Code (e.g. PHM-001)",
                    example = "PHM-001",
                    required = true
            )
            @RequestParam("pharmacy_code") String pharmacyCode) {

        List<OneRxBrandResponseDto> response = oneRxBrandService.getAvailableBrandsForCommaSeparatedDrugs(drugCode, pharmacyCode);
        return ResponseEntity.ok(response);
    }
}
