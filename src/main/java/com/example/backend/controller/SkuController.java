package com.example.backend.controller;

import com.example.backend.dto.sku.SkuDropdownDto;
import com.example.backend.dto.sku.SkuRequestDto;
import com.example.backend.dto.sku.SkuResponseDto;
import com.example.backend.dto.sku.SkuStockAdjustmentDto;
import com.example.backend.service.SkuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/skus")
@Tag(name = "SKU & Inventory", description = "Endpoints for managing Brand SKUs, Bin Card dropdowns, and stock adjustments")
public class SkuController {

    private final SkuService skuService;

    public SkuController(SkuService skuService) {
        this.skuService = skuService;
    }

    // 1. CREATE BRAND SKU (Pharmacist, SuperAdmin, Admin, or Procurement for inline brand creation)
    @PostMapping
    @Operation(summary = "Create Brand SKU", description = "Creates a new brand SKU linked to a GenericStockSetting (auto-creating the generic pool if new).")
    public ResponseEntity<SkuResponseDto> createSku(@Valid @RequestBody SkuRequestDto request) {
        SkuResponseDto response = skuService.createSku(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. GET LIGHTWEIGHT DROPDOWN SKUS (For Bin Card brand selector)
    @GetMapping("/dropdown")
    @Operation(summary = "Get Dropdown SKUs", description = "Returns lightweight list of active brand SKUs for Bin Card and batch selection.")
    public ResponseEntity<List<SkuDropdownDto>> getDropdownSkus(
            @Parameter(description = "ID of the pharmacy", required = true)
            @RequestParam Long pharmacyId) {
        return ResponseEntity.ok(skuService.getDropdownSkus(pharmacyId));
    }

    // 3. UPDATE BRAND SKU
    @PutMapping("/{id:[0-9]+}")
    @Operation(summary = "Update Brand SKU", description = "Updates brand SKU details (brand name, SKU code, packaging, status).")
    public ResponseEntity<SkuResponseDto> updateSku(
            @Parameter(description = "SKU ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody SkuRequestDto request) {
        return ResponseEntity.ok(skuService.updateSku(id, request));
    }

    // 4. DEACTIVATE SKU (Soft Delete - Sets status to Inactive)
    @DeleteMapping("/{id:[0-9]+}")
    @Operation(summary = "Deactivate SKU", description = "Sets SKU status to Inactive instead of permanently deleting it, preserving historical batches and transactions.")
    public ResponseEntity<String> deleteSku(
            @Parameter(description = "SKU ID", required = true)
            @PathVariable Long id) {
        skuService.deleteSku(id);
        return ResponseEntity.ok("SKU set to inactive successfully");
    }

    // 5. REACTIVATE SKU (Sets status back to Active)
    @PatchMapping("/{id:[0-9]+}/reactivate")
    @Operation(summary = "Reactivate SKU", description = "Sets SKU status back to Active.")
    public ResponseEntity<SkuResponseDto> reactivateSku(
            @Parameter(description = "SKU ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(skuService.reactivateSku(id));
    }

    // 6. ADJUST SKU STOCK
    @PatchMapping("/{id:[0-9]+}/adjust-stock")
    @Operation(summary = "Adjust SKU Stock Quantity", description = "Adjusts stock quantity up or down for a specific brand SKU and syncs pooled generic stock.")
    public ResponseEntity<SkuResponseDto> adjustStock(
            @Parameter(description = "SKU ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody SkuStockAdjustmentDto request) {
        return ResponseEntity.ok(skuService.adjustStock(id, request));
    }
}
