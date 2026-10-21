package com.example.backend.controller;

import com.example.backend.dto.inventory.InventoryLedgerResponseDto;
import com.example.backend.dto.inventory.InventorySkuSummaryDto;
import com.example.backend.dto.inventory.InventoryTransactionResponseDto;
import com.example.backend.entity.InventoryTransaction;
import com.example.backend.service.InventoryTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inventory-transactions")
@Tag(name = "Inventory Transactions & Stock Ledger", description = "Endpoints for viewing chronological inventory transaction movements, stock ledgers, and Bin Card records.")
public class InventoryTransactionController {

    private final InventoryTransactionService inventoryTransactionService;

    public InventoryTransactionController(InventoryTransactionService inventoryTransactionService) {
        this.inventoryTransactionService = inventoryTransactionService;
    }

    // 1. GET INVENTORY LEDGER FOR SKU (BIN CARD)
    @GetMapping("/ledger")
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    @Operation(summary = "Get SKU Inventory Ledger (Bin Card)", description = "Retrieves the complete historical ledger and running balances for a specific SKU in a facility.")
    public ResponseEntity<InventoryLedgerResponseDto> getLedger(
            @Parameter(description = "Operating facility ID", required = true)
            @RequestParam Long facilityId,
            @Parameter(description = "SKU ID", required = true)
            @RequestParam Long skuId,
            @Parameter(description = "Optional start date filter (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Optional end date filter (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(inventoryTransactionService.getLedgerForSku(facilityId, skuId, startDate, endDate));
    }

    // 2. GET SKUS FOR LEDGER DROPDOWN
    @GetMapping("/skus")
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    @Operation(summary = "Get Facility SKUs for Ledger", description = "Retrieves active SKUs within the facility to populate the Bin Card selection dropdown.")
    public ResponseEntity<List<InventorySkuSummaryDto>> getFacilitySkusForLedger(
            @Parameter(description = "Operating facility ID", required = true)
            @RequestParam Long facilityId) {
        return ResponseEntity.ok(inventoryTransactionService.getSkusForLedger(facilityId));
    }

    // 3. GET TRANSACTIONS BY FACILITY (WITH OPTIONAL TYPE FILTER)
    @GetMapping
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    @Operation(summary = "Get Inventory Transactions by Facility", description = "Retrieves transactions across the facility, optionally filtered by transaction types.")
    public ResponseEntity<List<InventoryTransactionResponseDto>> getTransactionsByFacility(
            @Parameter(description = "Operating facility ID", required = true)
            @RequestParam Long facilityId,
            @Parameter(description = "Optional transaction type filter (e.g. ADJUSTMENT_ADD, ADJUSTMENT_SUB, DISPENSE)")
            @RequestParam(required = false) List<InventoryTransaction.Type> types) {
        return ResponseEntity.ok(inventoryTransactionService.getTransactionsByFacility(facilityId, types));
    }

    // 4. GET TRANSACTIONS BY SKU
    @GetMapping("/sku/{skuId}")
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    @Operation(summary = "Get Inventory Transactions by SKU", description = "Retrieves transactions for a specific SKU, optionally filtered by transaction types.")
    public ResponseEntity<List<InventoryTransactionResponseDto>> getTransactionsBySku(
            @Parameter(description = "SKU ID", required = true)
            @PathVariable Long skuId,
            @Parameter(description = "Optional transaction type filter")
            @RequestParam(required = false) List<InventoryTransaction.Type> types) {
        return ResponseEntity.ok(inventoryTransactionService.getTransactionsBySku(skuId, types));
    }
}
