package com.example.backend.controller;

import com.example.backend.dto.location.LibLocationRequestDto;
import com.example.backend.dto.location.LibLocationResponseDto;
import com.example.backend.entity.LibLocation;
import com.example.backend.service.LibLocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lib-locations")
@Tag(name = "Locations", description = "Endpoints for managing physical pharmacy storage locations (shelves, cold storage, freezers)")
public class LibLocationController {

    private final LibLocationService libLocationService;

    public LibLocationController(LibLocationService libLocationService) {
        this.libLocationService = libLocationService;
    }

    // 1. CREATE LOCATION
    @PostMapping
    @Operation(summary = "Create Storage Location", description = "Registers a new storage location. If code is omitted, it is auto-generated (e.g. SHF-001, REF-001).")
    public ResponseEntity<LibLocationResponseDto> createLocation(@Valid @RequestBody LibLocationRequestDto request) {
        LibLocationResponseDto response = libLocationService.createLocation(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. GET ALL LOCATIONS
    @GetMapping
    @Operation(summary = "Get All Storage Locations", description = "Returns storage locations, optionally filtered by facility, active status, or storage type.")
    public ResponseEntity<List<LibLocationResponseDto>> getAllLocations(
            @Parameter(description = "Optional facility ID filter")
            @RequestParam(required = false) Long facilityId,
            @Parameter(description = "Filter only active locations (default: true for dropdowns)")
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly,
            @Parameter(description = "Filter by storage condition type")
            @RequestParam(required = false) LibLocation.StorageType storageType) {
        return ResponseEntity.ok(libLocationService.getAllLocations(facilityId, activeOnly, storageType));
    }

    // 3. GET LOCATION BY ID
    @GetMapping("/{id}")
    @Operation(summary = "Get Location by ID", description = "Retrieves details of a specific storage location.")
    public ResponseEntity<LibLocationResponseDto> getLocationById(
            @Parameter(description = "Location ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(libLocationService.getLocationById(id));
    }

    // 4. UPDATE LOCATION
    @PutMapping("/{id}")
    @Operation(summary = "Update Location", description = "Updates code, storage type, description, or active status.")
    public ResponseEntity<LibLocationResponseDto> updateLocation(
            @Parameter(description = "Location ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody LibLocationRequestDto request) {
        return ResponseEntity.ok(libLocationService.updateLocation(id, request));
    }

    // 5. DELETE LOCATION
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Location", description = "Deletes a location if unused, or deactivates it if referenced by batches.")
    public ResponseEntity<String> deleteLocation(
            @Parameter(description = "Location ID", required = true)
            @PathVariable Long id) {
        libLocationService.deleteLocation(id);
        return ResponseEntity.ok("Location deleted successfully");
    }

    // 6. SUGGEST NEXT CODE
    @GetMapping("/suggest-code")
    @Operation(summary = "Suggest Next Code", description = "Returns the next available auto-generated code for a given storage type without saving.")
    public ResponseEntity<String> suggestNextCode(
            @Parameter(description = "Storage condition type", required = true)
            @RequestParam LibLocation.StorageType storageType,
            @Parameter(description = "Optional facility ID")
            @RequestParam(required = false) Long facilityId) {
        return ResponseEntity.ok(libLocationService.generateNextCode(storageType, facilityId));
    }
}
