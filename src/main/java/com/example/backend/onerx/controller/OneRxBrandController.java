package com.example.backend.onerx.controller;

import com.example.backend.onerx.dto.OneRxBrandResponseDto;
import com.example.backend.onerx.service.OneRxBrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/brands")
@Tag(name = "OneRx Integration - Brand Availability", description = "Endpoints for OneRx system integration to query available medication brands and SKU quantities by facility.")
public class OneRxBrandController {

    private final OneRxBrandService oneRxBrandService;

    public OneRxBrandController(OneRxBrandService oneRxBrandService) {
        this.oneRxBrandService = oneRxBrandService;
    }

    /**
     * GET /api/brands?drug_code=DRG-000123&facility_code=FAC-001
     * GET /api/brands?drug_code=DRG-000123,DRG-000456&facility_code=FAC-001
     *
     * Headers:
     * x-api-key: <key> OR Authorization: Bearer <token>
     * Accept: application/json
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get available brand SKUs for generic drug(s) in a facility for OneRx",
            description = "Allows the OneRx 3rd-party system to query current in-stock brands and SKU packaging units for comma-separated drug codes within a specific facility."
    )
    public ResponseEntity<List<OneRxBrandResponseDto>> getAvailableBrands(
            @Parameter(
                    description = "Generic Drug Code(s), comma-separated (e.g. DRG-000123 or DRG-000123,DRG-000456)",
                    example = "DRG-000123,DRG-000456",
                    required = true
            )
            @RequestParam("drug_code") String drugCode,

            @Parameter(
                    description = "Facility Code (e.g. FAC-001)",
                    example = "FAC-001",
                    required = true
            )
            @RequestParam("facility_code") String facilityCode) {

        List<OneRxBrandResponseDto> response = oneRxBrandService.getAvailableBrandsForCommaSeparatedDrugs(drugCode, facilityCode);
        return ResponseEntity.ok(response);
    }
}
