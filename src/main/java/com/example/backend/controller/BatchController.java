package com.example.backend.controller;

import com.example.backend.dto.batch.BatchReleaseRequestDto;
import com.example.backend.dto.batch.BatchRequestDto;
import com.example.backend.dto.batch.BatchResponseDto;
import com.example.backend.dto.batch.BatchSummaryDto;
import com.example.backend.dto.common.PageResponseDto;
import com.example.backend.entity.Batch;
import com.example.backend.service.BatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batches")
@Tag(name = "Batches & Expiry", description = "Endpoints for receiving batches, batch tracking, quarantine/release actions, and expiry processing")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    // 1. RECEIVE SINGLE BATCH
    @PostMapping
    @Operation(summary = "Receive Single Batch", description = "Records a newly received batch into inventory and automatically increments the associated SKU stock.")
    public ResponseEntity<BatchResponseDto> receiveBatch(@Valid @RequestBody BatchRequestDto request) {
        BatchResponseDto response = batchService.receiveBatch(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. RECEIVE MULTIPLE BATCHES BULK
    @PostMapping("/bulk")
    @Operation(summary = "Receive Batches in Bulk", description = "Receives multiple batches in a single transaction (e.g. when receiving multi-item purchase orders).")
    public ResponseEntity<List<BatchResponseDto>> receiveBatchesBulk(@RequestBody List<BatchRequestDto> requests) {
        List<BatchResponseDto> response = batchService.receiveBatchesBulk(requests);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 3. GET BATCHES PAGINATED (Search strictly by batchNum)
    @GetMapping
    @Operation(summary = "Get Batches Paginated", description = "Retrieves paginated batches by facility with optional batchNumber search and status/sku filter.")
    public ResponseEntity<PageResponseDto<BatchResponseDto>> getBatchesPaginated(
            @Parameter(description = "Facility ID", required = true)
            @RequestParam Long facilityId,
            @Parameter(description = "Search keyword (searches batchNumber or SKU)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Optional batch status filter (ALL, ACTIVE, QUARANTINED, EXPIRED, DEPLETED)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Optional SKU name filter")
            @RequestParam(required = false) String sku,
            @Parameter(description = "Optional expiry filter (ALL, NEAR_EXPIRY, EXPIRED, HEALTHY)")
            @RequestParam(required = false) String expiryFilter,
            @Parameter(description = "Page index (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort property (receivedAt, batchNumber, etc.)")
            @RequestParam(defaultValue = "receivedAt") String sortBy,
            @Parameter(description = "Sort direction (ASC, DESC)")
            @RequestParam(defaultValue = "DESC") String sortDir) {
        return ResponseEntity.ok(batchService.getBatchesPaginated(
                facilityId, search, status, sku, expiryFilter, page, size, sortBy, sortDir));
    }

    // 3c. GET BATCH SUMMARY KPIS
    @GetMapping("/summary")
    @Operation(summary = "Get Batch Summary KPIs", description = "Returns aggregated counts for total batches, active stock, expiry alerts, and quarantined batches.")
    public ResponseEntity<BatchSummaryDto> getBatchSummary(
            @Parameter(description = "Facility ID", required = true)
            @RequestParam Long facilityId) {
        return ResponseEntity.ok(batchService.getBatchSummary(facilityId));
    }

    // 3d. GET DISTINCT SKUS IN BATCHES
    @GetMapping("/skus")
    @Operation(summary = "Get Distinct Batch SKUs", description = "Returns distinct SKU names present in batches for filter dropdown.")
    public ResponseEntity<List<String>> getDistinctBatchSkus(
            @Parameter(description = "Facility ID", required = true)
            @RequestParam Long facilityId) {
        return ResponseEntity.ok(batchService.getDistinctBatchSkus(facilityId));
    }

    // 3e. GET BATCHES BY SKU
    @GetMapping("/sku/{skuId}")
    @Operation(summary = "Get Batches by SKU", description = "Retrieves all batches for a specific SKU in a facility (ordered by receivedAt ascending).")
    public ResponseEntity<List<BatchResponseDto>> getBatchesBySku(
            @Parameter(description = "SKU ID", required = true)
            @PathVariable Long skuId,
            @Parameter(description = "Facility ID", required = true)
            @RequestParam Long facilityId) {
        return ResponseEntity.ok(batchService.getBatchesBySku(facilityId, skuId));
    }


    // 4. GET BATCH BY ID
    @GetMapping("/{id}")
    @Operation(summary = "Get Batch by ID", description = "Retrieves details of a specific batch by its ID.")
    public ResponseEntity<BatchResponseDto> getBatchById(
            @Parameter(description = "Batch ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(batchService.getBatchById(id));
    }

    // 5. UPDATE BATCH STATUS
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update Batch Status", description = "Changes a batch's status (AVAILABLE, QUARANTINED, DEPLETED, EXPIRED) and appends notes.")
    public ResponseEntity<BatchResponseDto> updateBatchStatus(
            @Parameter(description = "Batch ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Target status", required = true)
            @RequestParam Batch.Status status,
            @Parameter(description = "Optional notes or reason for status change")
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(batchService.updateBatchStatus(id, status, notes));
    }

    // 5b. RELEASE QUARANTINED BATCH
    @PostMapping("/{id}/release")
    @Operation(summary = "Release Quarantined Batch", description = "Releases a quarantined batch to Available with optional partial accepted units and discarded units logged with reasons.")
    public ResponseEntity<BatchResponseDto> releaseBatchFromQuarantine(
            @Parameter(description = "Batch ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody BatchReleaseRequestDto request) {
        return ResponseEntity.ok(batchService.releaseBatchFromQuarantine(id, request));
    }

    // 5c. UPDATE BATCH LOCATION (Relocate)
    @PatchMapping("/{id}/location")
    @Operation(summary = "Update Batch Location", description = "Moves or assigns a batch to a specific physical storage location (shelf, cold storage, etc.).")
    public ResponseEntity<BatchResponseDto> updateBatchLocation(
            @Parameter(description = "Batch ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Storage Location ID (null to unassign)")
            @RequestParam(required = false) Long locationId) {
        return ResponseEntity.ok(batchService.updateBatchLocation(id, locationId));
    }

    // 6. PROCESS EXPIRED BATCHES FOR A SKU
    @PostMapping("/process-expired")
    @Operation(summary = "Process Expired Batches for SKU", description = "Scans batches past their expiry date for a required facility and SKU, marks them EXPIRED, decrements active SKU stock, and logs audit events.")
    public ResponseEntity<Map<String, Object>> processExpiredBatches(
            @Parameter(description = "Facility ID (required)", required = true)
            @RequestParam Long facilityId,
            @Parameter(description = "SKU ID (required)", required = true)
            @RequestParam Long skuId) {
        int count = batchService.processExpiredBatches(facilityId, skuId);
        return ResponseEntity.ok(Map.of(
                "count", count,
                "message", count > 0
                        ? "Successfully processed and deducted " + count + " expired batch(es)."
                        : "No expired batches found to process for this SKU."
        ));
    }
}
