package com.example.backend.controller;

import com.example.backend.dto.common.PageResponseDto;
import com.example.backend.dto.inventory.InventoryLedgerResponseDto;
import com.example.backend.dto.inventory.InventoryTransactionResponseDto;
import com.example.backend.dto.inventory.InventoryTransactionSummaryDto;
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

    // 3. GET TRANSACTIONS (PAGINATED, WITH OPTIONAL FACILITY, SKU, TYPE, AND KEYWORD FILTERS)
    @GetMapping
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    @Operation(summary = "Get Inventory Transactions Paginated", description = "Retrieves paginated transactions across the facility, optionally filtered by SKU, transaction types and search keyword.")
    public ResponseEntity<PageResponseDto<InventoryTransactionResponseDto>> getTransactions(
            @Parameter(description = "Operating facility ID", required = true)
            @RequestParam Long facilityId,
            @Parameter(description = "Optional SKU ID filter")
            @RequestParam(required = false) Long skuId,
            @Parameter(description = "Optional transaction type filter (e.g. ADJUSTMENT_ADD, ADJUSTMENT_SUB, DISPENSE)")
            @RequestParam(required = false) List<InventoryTransaction.Type> types,
            @Parameter(description = "Optional keyword search across reference, batchNum, SKU name, or reason")
            @RequestParam(required = false) String search,
            @Parameter(description = "Page index (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort property (createdAt, referenceNumber, transactionType)")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (ASC, DESC)")
            @RequestParam(defaultValue = "DESC") String sortDir) {
        return ResponseEntity.ok(inventoryTransactionService.getTransactionsPaginated(
                facilityId, skuId, types, search, page, size, sortBy, sortDir));
    }

    // 4. GET TRANSACTIONS BY SKU (PAGINATED)
    @GetMapping("/sku/{skuId}")
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    @Operation(summary = "Get Inventory Transactions by SKU (Paginated)", description = "Retrieves paginated transactions for a specific SKU, scoped to the facility.")
    public ResponseEntity<PageResponseDto<InventoryTransactionResponseDto>> getTransactionsBySku(
            @Parameter(description = "SKU ID", required = true)
            @PathVariable Long skuId,
            @Parameter(description = "Operating facility ID", required = true)
            @RequestParam Long facilityId,
            @Parameter(description = "Optional transaction type filter")
            @RequestParam(required = false) List<InventoryTransaction.Type> types,
            @Parameter(description = "Optional keyword search")
            @RequestParam(required = false) String search,
            @Parameter(description = "Page index (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort property (createdAt, referenceNumber, transactionType)")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (ASC, DESC)")
            @RequestParam(defaultValue = "DESC") String sortDir) {
        return ResponseEntity.ok(inventoryTransactionService.getTransactionsPaginated(
                facilityId, skuId, types, search, page, size, sortBy, sortDir));
    }

    // 5. GET TRANSACTION SUMMARY KPIS
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    @Operation(summary = "Get Inventory Transaction Summary KPIs", description = "Returns aggregated facility-level transaction metrics (additions, deductions, net delta) for KPI cards.")
    public ResponseEntity<InventoryTransactionSummaryDto> getTransactionSummary(
            @Parameter(description = "Operating facility ID", required = true)
            @RequestParam Long facilityId,
            @Parameter(description = "Optional transaction type filter (e.g. ADJUSTMENT_ADD, ADJUSTMENT_SUB, DISPENSE)")
            @RequestParam(required = false) List<InventoryTransaction.Type> types) {
        return ResponseEntity.ok(inventoryTransactionService.getTransactionSummary(facilityId, types));
    }
}
