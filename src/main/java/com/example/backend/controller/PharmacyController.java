package com.example.backend.controller;

import com.example.backend.dto.pharmacy.PharmacyRequestDto;
import com.example.backend.dto.pharmacy.PharmacyResponseDto;
import com.example.backend.service.PharmacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacies")
@Tag(name = "Pharmacies", description = "Endpoints for managing pharmacies linked to facilities")
public class PharmacyController {

    private final PharmacyService pharmacyService;

    public PharmacyController(PharmacyService pharmacyService) {
        this.pharmacyService = pharmacyService;
    }

    // 1. CREATE PHARMACY
    @PostMapping
    @Operation(summary = "Create Pharmacy", description = "Creates a new pharmacy under a facility (SuperAdmin).")
    public ResponseEntity<PharmacyResponseDto> createPharmacy(@Valid @RequestBody PharmacyRequestDto request) {
        PharmacyResponseDto response = pharmacyService.createPharmacy(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. GET ALL PHARMACIES (optionally filtered by facilityId)
    @GetMapping
    @Operation(summary = "Get Pharmacies", description = "Retrieves all pharmacies or pharmacies associated with a specific facility.")
    public ResponseEntity<List<PharmacyResponseDto>> getPharmacies(
            @Parameter(description = "Optional Facility ID filter")
            @RequestParam(required = false) Long facilityId) {
        return ResponseEntity.ok(pharmacyService.getPharmacies(facilityId));
    }

    // 3. GET PHARMACY BY ID
    @GetMapping("/{id}")
    @Operation(summary = "Get Pharmacy by ID", description = "Retrieves details of a specific pharmacy.")
    public ResponseEntity<PharmacyResponseDto> getPharmacyById(
            @Parameter(description = "Pharmacy ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(pharmacyService.getPharmacyById(id));
    }

    // 4. UPDATE PHARMACY
    @PutMapping("/{id}")
    @Operation(summary = "Update Pharmacy", description = "Updates details, license number, address, or name of a pharmacy (SuperAdmin).")
    public ResponseEntity<PharmacyResponseDto> updatePharmacy(
            @Parameter(description = "Pharmacy ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody PharmacyRequestDto request) {
        return ResponseEntity.ok(pharmacyService.updatePharmacy(id, request));
    }

    // 5. DELETE PHARMACY
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Pharmacy", description = "Removes a pharmacy from the facility (SuperAdmin).")
    public ResponseEntity<String> deletePharmacy(
            @Parameter(description = "Pharmacy ID", required = true)
            @PathVariable Long id) {
        pharmacyService.deletePharmacy(id);
        return ResponseEntity.ok("Pharmacy deleted successfully");
    }
}

