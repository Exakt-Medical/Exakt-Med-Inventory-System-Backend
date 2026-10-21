package com.example.backend.controller;

import com.example.backend.dto.assign.FacilityAssignRequestDto;
import com.example.backend.dto.facility.FacilityResponseDto;
import com.example.backend.dto.user.UserResponseDto;
import com.example.backend.service.FacilityAssignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assign/facilities")
@Tag(name = "Facility Assignments", description = "Endpoints for assigning users (Pharmacists, Procurement Officers) to facilities and querying assigned scopes")
public class FacilityAssignController {

    private final FacilityAssignService facilityAssignService;

    public FacilityAssignController(FacilityAssignService facilityAssignService) {
        this.facilityAssignService = facilityAssignService;
    }

    // 1. ASSIGN USERS TO FACILITY
    @PostMapping("/{facilityId}")
    @Operation(summary = "Assign Users to Facility", description = "Associates one or more user IDs to a specific facility.")
    public ResponseEntity<String> assignUsersToFacility(
            @Parameter(description = "Facility ID", required = true)
            @PathVariable Long facilityId,
            @Valid @RequestBody FacilityAssignRequestDto request) {
        String result = facilityAssignService.assignUsersToFacility(facilityId, request);
        return ResponseEntity.ok(result);
    }

    // 2. UNASSIGN USERS FROM FACILITY
    @DeleteMapping("/{facilityId}")
    @Operation(summary = "Unassign Users from Facility", description = "Removes one or more users from a facility assignment.")
    public ResponseEntity<String> unassignUsersFromFacility(
            @Parameter(description = "Facility ID", required = true)
            @PathVariable Long facilityId,
            @Valid @RequestBody FacilityAssignRequestDto request) {
        String result = facilityAssignService.unassignUsersFromFacility(facilityId, request);
        return ResponseEntity.ok(result);
    }

    // 3. GET ASSIGNED FACILITIES FOR CURRENT USER
    @GetMapping("/my-facilities")
    @Operation(summary = "Get My Assigned Facilities", description = "Retrieves all facilities assigned to the currently authenticated user based on their JWT token.")
    public ResponseEntity<List<FacilityResponseDto>> getMyAssignedFacilities() {
        return ResponseEntity.ok(facilityAssignService.getMyAssignedFacilities());
    }

    // 4. GET ALL USERS ASSIGNED TO A FACILITY
    @GetMapping("/{facilityId}/users")
    @Operation(summary = "Get Assigned Users by Facility", description = "Retrieves all users assigned to a specific facility.")
    public ResponseEntity<List<UserResponseDto>> getUsersByFacilityId(
            @Parameter(description = "Facility ID", required = true)
            @PathVariable Long facilityId) {
        return ResponseEntity.ok(facilityAssignService.getUsersByFacilityId(facilityId));
    }
}
