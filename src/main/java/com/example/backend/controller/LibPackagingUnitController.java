package com.example.backend.controller;

import com.example.backend.dto.packaging.LibPackagingUnitResponseDto;
import com.example.backend.service.LibPackagingUnitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lib-packaging-units")
@Tag(name = "Packaging Units", description = "Endpoints for standardized pharmaceutical packaging units (sheets, boxes, bottles, etc.)")
public class LibPackagingUnitController {

    private final LibPackagingUnitService libPackagingUnitService;

    public LibPackagingUnitController(LibPackagingUnitService libPackagingUnitService) {
        this.libPackagingUnitService = libPackagingUnitService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get All Packaging Units", description = "Returns the standardized list of packaging units for dropdown selections.")
    public ResponseEntity<List<LibPackagingUnitResponseDto>> getAllPackagingUnits() {
        return ResponseEntity.ok(libPackagingUnitService.getAllPackagingUnits());
    }
}
