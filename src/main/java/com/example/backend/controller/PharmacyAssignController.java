package com.example.backend.controller;

import com.example.backend.dto.assign.PharmacyAssignRequestDto;
import com.example.backend.dto.pharmacy.PharmacyResponseDto;
import com.example.backend.dto.user.UserResponseDto;
import com.example.backend.service.PharmacyAssignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assign/pharmacies")
@Tag(name = "Pharmacy Assignments", description = "Endpoints for assigning users (Admin, Pharmacists, Procurement Officers) to pharmacies and querying assigned scopes")
public class PharmacyAssignController {

    private final PharmacyAssignService pharmacyAssignService;

    public PharmacyAssignController(PharmacyAssignService pharmacyAssignService) {
        this.pharmacyAssignService = pharmacyAssignService;
    }

    // 1. ASSIGN USERS TO PHARMACY
    @PostMapping("/{pharmacyId}")
    @Operation(summary = "Assign Users to Pharmacy", description = "Associates one or more user IDs to a specific pharmacy.")
    public ResponseEntity<String> assignUsersToPharmacy(
            @Parameter(description = "Pharmacy ID", required = true)
            @PathVariable Long pharmacyId,
            @Valid @RequestBody PharmacyAssignRequestDto request) {
        String result = pharmacyAssignService.assignUsersToPharmacy(pharmacyId, request);
        return ResponseEntity.ok(result);
    }

    // 2. UNASSIGN USERS FROM PHARMACY
    @DeleteMapping("/{pharmacyId}")
    @Operation(summary = "Unassign Users from Pharmacy", description = "Removes one or more users from a pharmacy assignment.")
    public ResponseEntity<String> unassignUsersFromPharmacy(
            @Parameter(description = "Pharmacy ID", required = true)
            @PathVariable Long pharmacyId,
            @Valid @RequestBody PharmacyAssignRequestDto request) {
        String result = pharmacyAssignService.unassignUsersFromPharmacy(pharmacyId, request);
        return ResponseEntity.ok(result);
    }

    // 3. GET ASSIGNED PHARMACIES FOR CURRENT USER
    @GetMapping("/my-pharmacies")
    @Operation(summary = "Get My Assigned Pharmacies", description = "Retrieves all pharmacies assigned to the currently authenticated user based on their JWT token.")
    public ResponseEntity<List<PharmacyResponseDto>> getMyAssignedPharmacies() {
        return ResponseEntity.ok(pharmacyAssignService.getMyAssignedPharmacies());
    }

    // 4. GET ALL USERS ASSIGNED TO A PHARMACY
    @GetMapping("/{pharmacyId}/users")
    @Operation(summary = "Get Assigned Users by Pharmacy", description = "Retrieves all users assigned to a specific pharmacy.")
    public ResponseEntity<List<UserResponseDto>> getUsersByPharmacyId(
            @Parameter(description = "Pharmacy ID", required = true)
            @PathVariable Long pharmacyId) {
        return ResponseEntity.ok(pharmacyAssignService.getUsersByPharmacyId(pharmacyId));
    }
}

