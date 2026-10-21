package com.example.backend.controller;

import com.example.backend.dto.supplier.SupplierRequestDto;
import com.example.backend.dto.supplier.SupplierResponseDto;
import com.example.backend.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@Tag(name = "Suppliers", description = "Endpoints for managing pharmaceutical suppliers, contact details, lead times, and payment terms")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    // 1. CREATE SUPPLIER
    @PostMapping
    @Operation(summary = "Create Supplier", description = "Registers a new pharmaceutical vendor/supplier.")
    public ResponseEntity<SupplierResponseDto> createSupplier(@Valid @RequestBody SupplierRequestDto request) {
        SupplierResponseDto response = supplierService.createSupplier(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. GET ALL SUPPLIERS
    @GetMapping
    @Operation(summary = "Get All Suppliers", description = "Retrieves all suppliers, optionally filtered by assigned facility.")
    public ResponseEntity<List<SupplierResponseDto>> getAllSuppliers(
            @Parameter(description = "Optional facility ID filter")
            @RequestParam(required = false) Long facilityId) {
        return ResponseEntity.ok(supplierService.getAllSuppliers(facilityId));
    }

    // 3. GET SUPPLIER BY ID
    @GetMapping("/{id}")
    @Operation(summary = "Get Supplier by ID", description = "Retrieves details of a specific supplier.")
    public ResponseEntity<SupplierResponseDto> getSupplierById(
            @Parameter(description = "Supplier ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getSupplierById(id));
    }

    // 4. UPDATE SUPPLIER
    @PutMapping("/{id}")
    @Operation(summary = "Update Supplier", description = "Updates supplier name, contact details, payment terms, or lead time.")
    public ResponseEntity<SupplierResponseDto> updateSupplier(
            @Parameter(description = "Supplier ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequestDto request) {
        return ResponseEntity.ok(supplierService.updateSupplier(id, request));
    }

    // 5. DELETE SUPPLIER
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Supplier", description = "Removes a supplier from the system.")
    public ResponseEntity<String> deleteSupplier(
            @Parameter(description = "Supplier ID", required = true)
            @PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.ok("Supplier deleted successfully");
    }
}
