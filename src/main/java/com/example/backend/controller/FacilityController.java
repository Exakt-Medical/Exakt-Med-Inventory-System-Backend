package com.example.backend.controller;

import com.example.backend.dto.facility.FacilityRequestDto;
import com.example.backend.dto.facility.FacilityResponseDto;
import com.example.backend.service.FacilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/facilities")
@Tag(name = "Facilities", description = "Endpoints for managing healthcare facilities, pharmacies, and clinics linked to projects")
public class FacilityController {

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    // 1. CREATE FACILITY
    @PostMapping
    @Operation(summary = "Create Facility", description = "Creates a new clinic or hospital facility under a project.")
    public ResponseEntity<FacilityResponseDto> createFacility(@Valid @RequestBody FacilityRequestDto request) {
        FacilityResponseDto response = facilityService.createFacility(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. GET ALL FACILITIES BY PROJECT ID
    @GetMapping
    @Operation(summary = "Get Facilities by Project ID", description = "Retrieves all facilities associated with a specific project.")
    public ResponseEntity<List<FacilityResponseDto>> getFacilitiesByProjectId(
            @Parameter(description = "Project ID", required = true)
            @RequestParam Long projectId) {
        return ResponseEntity.ok(facilityService.getFacilitiesByProjectId(projectId));
    }

    // 4. UPDATE FACILITY
    @PutMapping("/{id}")
    @Operation(summary = "Update Facility", description = "Updates details, address, or name of a facility.")
    public ResponseEntity<FacilityResponseDto> updateFacility(
            @Parameter(description = "Facility ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody FacilityRequestDto request) {
        return ResponseEntity.ok(facilityService.updateFacility(id, request));
    }

    // 5. DELETE FACILITY
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Facility", description = "Removes a facility from the project.")
    public ResponseEntity<String> deleteFacility(
            @Parameter(description = "Facility ID", required = true)
            @PathVariable Long id) {
        facilityService.deleteFacility(id);
        return ResponseEntity.ok("Facility deleted successfully");
    }
}
