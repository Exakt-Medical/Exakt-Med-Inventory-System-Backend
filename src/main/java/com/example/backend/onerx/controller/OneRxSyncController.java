package com.example.backend.onerx.controller;

import com.example.backend.onerx.messaging.OneRxStockSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/onerx/sync")
@Tag(name = "OneRx Integration - RabbitMQ Sync", description = "Administrative endpoints to trigger OneRx RabbitMQ stock snapshot seeding.")
public class OneRxSyncController {

    private final ObjectProvider<OneRxStockSnapshotService> snapshotServiceProvider;

    public OneRxSyncController(ObjectProvider<OneRxStockSnapshotService> snapshotServiceProvider) {
        this.snapshotServiceProvider = snapshotServiceProvider;
    }

    @PostMapping("/snapshot")
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'ONERX_INTEGRATION')")
    @Operation(
            summary = "Push full or single-pharmacy stock snapshot to OneRx via RabbitMQ",
            description = "Publishes active SKU unexpired stock balances to inventory.sync.exchange (routing key inventory.stock.snapshot) in batches of 200 items to seed OneRx's local cache."
    )
    public ResponseEntity<?> triggerStockSnapshot(
            @Parameter(description = "Optional pharmacy code (e.g. PHM-001). Omit to push snapshots for all pharmacies.")
            @RequestParam(value = "pharmacy_code", required = false) String pharmacyCode) {

        OneRxStockSnapshotService snapshotService = snapshotServiceProvider.getIfAvailable();
        if (snapshotService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "disabled",
                    "message", "RabbitMQ OneRx inventory sync is currently disabled (inventory.sync.messaging.enabled=false)."
            ));
        }

        try {
            OneRxStockSnapshotService.SnapshotResult result = snapshotService.pushStockSnapshot(pharmacyCode);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "failed",
                    "message", ex.getMessage()
            ));
        }
    }
}
