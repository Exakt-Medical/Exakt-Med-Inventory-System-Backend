package com.example.backend.controller;

import com.example.backend.dto.common.PageResponseDto;
import com.example.backend.dto.sku.ReorderNeededSummaryDto;
import com.example.backend.dto.sku.SkuDropdownDto;
import com.example.backend.dto.sku.SkuRequestDto;
import com.example.backend.dto.sku.SkuResponseDto;
import com.example.backend.dto.sku.SkuStockAdjustmentDto;
import com.example.backend.dto.sku.SkuSummaryDto;
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
@Tag(name = "SKU & Inventory", description = "Endpoints for managing Stock Keeping Units (medicines/items), inventory levels, stock adjustments, and history logs")
public class SkuController {

    private final SkuService skuService;

    public SkuController(SkuService skuService) {
        this.skuService = skuService;
    }

    // 1. CREATE SKU
    @PostMapping
    @Operation(summary = "Create SKU", description = "Creates a new Stock Keeping Unit linked to a pharmacy and optional library medicine template.")
    public ResponseEntity<SkuResponseDto> createSku(@Valid @RequestBody SkuRequestDto request) {
        SkuResponseDto response = skuService.createSku(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. GET SKUS PAGINATED (Server-side paginated search & status/stock filtering)
    @GetMapping
    @Operation(summary = "Get SKUs Paginated", description = "Retrieves paginated SKUs by pharmacy with optional keyword search (brand or generic name), status filter (ALL, ACTIVE, INACTIVE), stock level filter (ALL, OPTIMAL, REORDER, CRITICAL, OUT_OF_STOCK), and sorting.")
    public ResponseEntity<PageResponseDto<SkuResponseDto>> getSkusPaginated(
            @Parameter(description = "ID of the pharmacy", required = true)
            @RequestParam Long pharmacyId,
            @Parameter(description = "Optional search keyword matching generic description or brand name")
            @RequestParam(required = false) String search,
            @Parameter(description = "Optional catalog status filter (ALL, ACTIVE, INACTIVE)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Optional stock level filter (ALL, OPTIMAL, REORDER, CRITICAL, OUT_OF_STOCK)")
            @RequestParam(required = false) String stockStatus,
            @Parameter(description = "Page index (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort property (brandName, name, units, etc.)")
            @RequestParam(defaultValue = "genericName") String sortBy,
            @Parameter(description = "Sort direction (ASC, DESC)")
            @RequestParam(defaultValue = "ASC") String sortDir) {
        return ResponseEntity.ok(skuService.getSkusPaginated(
                pharmacyId, search, status, stockStatus, page, size, sortBy, sortDir));
    }

    // 2b. GET SKU SUMMARY KPIS
    @GetMapping("/summary")
    @Operation(summary = "Get SKU Summary KPIs", description = "Returns aggregated pharmacy-level SKU counts for Total SKUs, Optimal, Reorder Needed, and Critical stock.")
    public ResponseEntity<SkuSummaryDto> getSkuSummary(
            @Parameter(description = "ID of the pharmacy", required = true)
            @RequestParam Long pharmacyId) {
        return ResponseEntity.ok(skuService.getSkuSummary(pharmacyId));
    }

    // 2c. GET LIGHTWEIGHT DROPDOWN SKUS
    @GetMapping("/dropdown")
    @Operation(summary = "Get Dropdown SKUs", description = "Returns lightweight list of SKUs for restock selection and modal dropdowns.")
    public ResponseEntity<List<SkuDropdownDto>> getDropdownSkus(
            @Parameter(description = "ID of the pharmacy", required = true)
            @RequestParam Long pharmacyId) {
        return ResponseEntity.ok(skuService.getDropdownSkus(pharmacyId));
    }

    // 2c. GET REORDER NEEDED KPI SUMMARY
    @GetMapping("/reorder-needed/summary")
    @Operation(summary = "Get Reorder Needed KPI Summary", description = "Returns total reorder count, critical shortage count, and reorder point triggered count for SKUs without active POs.")
    public ResponseEntity<ReorderNeededSummaryDto> getReorderNeededSummary(
            @Parameter(description = "ID of the pharmacy", required = true)
            @RequestParam Long pharmacyId) {
        return ResponseEntity.ok(skuService.getReorderNeededSummary(pharmacyId));
    }

    // 2d. GET SKUS THAT NEED REORDERING (For Procurement Purchase Orders & Pharmacist Reorder Quotations)
    @GetMapping("/reorder-needed")
    @Operation(summary = "Get SKUs Needing Reorder Paginated", description = "Returns paginated list of SKUs whose current stock has dropped below reorder level and does not already have an active pending/approved purchase order.")
    public ResponseEntity<PageResponseDto<SkuResponseDto>> getReorderNeededSkus(
            @Parameter(description = "ID of the pharmacy", required = true)
            @RequestParam Long pharmacyId,
            @Parameter(description = "Optional search query filter (matches brand name or generic name)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Stock status / urgency filter (ALL, CRITICAL, REORDER)")
            @RequestParam(required = false, defaultValue = "ALL") String stockStatus,
            @Parameter(description = "Page index (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort property (genericName, brandName, units, reorder, critical)")
            @RequestParam(defaultValue = "genericName") String sortBy,
            @Parameter(description = "Sort direction (ASC, DESC)")
            @RequestParam(defaultValue = "ASC") String sortDir) {
        return ResponseEntity.ok(skuService.getReorderNeededSkus(pharmacyId, search, stockStatus, page, size, sortBy, sortDir));
    }

    // 4. GET SKU BY ID
    @GetMapping("/{id:[0-9]+}")
    @Operation(summary = "Get SKU by ID", description = "Retrieves complete details of a specific SKU by its ID.")
    public ResponseEntity<SkuResponseDto> getSkuById(
            @Parameter(description = "SKU ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(skuService.getSkuById(id));
    }

    // 5. UPDATE SKU
    @PutMapping("/{id:[0-9]+}")
    @Operation(summary = "Update SKU", description = "Updates SKU details, dosage, form, min/max thresholds, unit price, and reorder points.")
    public ResponseEntity<SkuResponseDto> updateSku(
            @Parameter(description = "SKU ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody SkuRequestDto request) {
        return ResponseEntity.ok(skuService.updateSku(id, request));
    }

    // 6. DEACTIVATE SKU (Soft Delete - Sets status to Inactive)
    @DeleteMapping("/{id:[0-9]+}")
    @Operation(summary = "Deactivate SKU", description = "Sets SKU status to Inactive instead of permanently deleting it, preserving historical batches and transactions.")
    public ResponseEntity<String> deleteSku(
            @Parameter(description = "SKU ID", required = true)
            @PathVariable Long id) {
        skuService.deleteSku(id);
        return ResponseEntity.ok("SKU set to inactive successfully");
    }

    // 6b. REACTIVATE SKU (Sets status back to Active)
    @PatchMapping("/{id:[0-9]+}/reactivate")
    @Operation(summary = "Reactivate SKU", description = "Sets SKU status back to Active.")
    public ResponseEntity<SkuResponseDto> reactivateSku(
            @Parameter(description = "SKU ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(skuService.reactivateSku(id));
    }

    // 7. ADJUST SKU STOCK
    @PatchMapping("/{id:[0-9]+}/adjust-stock")
    @Operation(summary = "Adjust SKU Stock Quantity", description = "Adjusts stock quantity up or down for a specific SKU (audit reasons: Physical Count, Damage, Discrepancy, etc.) and logs the audit event.")
    public ResponseEntity<SkuResponseDto> adjustStock(
            @Parameter(description = "SKU ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody SkuStockAdjustmentDto request) {
        return ResponseEntity.ok(skuService.adjustStock(id, request));
    }
}
