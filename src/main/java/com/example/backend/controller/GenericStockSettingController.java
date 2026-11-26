package com.example.backend.controller;

import com.example.backend.dto.common.PageResponseDto;
import com.example.backend.dto.sku.*;
import com.example.backend.service.GenericStockSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/generic-stock-settings")
@Tag(name = "Generic Stock Settings", description = "Endpoints for generic-based pooled stock groups, shared thresholds, and reorder-needed queues")
public class GenericStockSettingController {

    private final GenericStockSettingService genericStockSettingService;

    public GenericStockSettingController(GenericStockSettingService genericStockSettingService) {
        this.genericStockSettingService = genericStockSettingService;
    }

    @GetMapping("/grouped")
    @Operation(summary = "Get Grouped Generic Stock Settings", description = "Returns paginated generic + packaging groups with pooled stock totals, shared thresholds, and nested brand SKUs.")
    public ResponseEntity<PageResponseDto<GenericStockGroupDto>> getGroupedSkus(
            @Parameter(description = "ID of the pharmacy", required = true)
            @RequestParam Long pharmacyId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String stockStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(genericStockSettingService.getGroupedSkus(
                pharmacyId, search, status, stockStatus, page, size));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get Generic Stock Summary KPIs", description = "Returns pharmacy-level generic group counts for Total, Optimal, Reorder Needed, and Critical stock.")
    public ResponseEntity<SkuSummaryDto> getSummary(@RequestParam Long pharmacyId) {
        return ResponseEntity.ok(genericStockSettingService.getSummary(pharmacyId));
    }

    @GetMapping("/reorder-needed/summary")
    @Operation(summary = "Get Generic Reorder Needed KPI Summary")
    public ResponseEntity<ReorderNeededSummaryDto> getReorderNeededSummary(@RequestParam Long pharmacyId) {
        return ResponseEntity.ok(genericStockSettingService.getReorderNeededSummary(pharmacyId));
    }

    @GetMapping("/reorder-needed")
    @Operation(summary = "Get Generic Groups Needing Reorder Paginated")
    public ResponseEntity<PageResponseDto<GenericStockGroupDto>> getReorderNeededGroups(
            @RequestParam Long pharmacyId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "ALL") String stockStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(genericStockSettingService.getReorderNeededGroups(
                pharmacyId, search, stockStatus, page, size));
    }

    @GetMapping("/dropdown")
    @Operation(summary = "Get Generic Stock Settings Dropdown", description = "Lightweight list of active generic groups for Pharmacist Reorder Quotation creation.")
    public ResponseEntity<List<GenericStockSettingDropdownDto>> getDropdown(@RequestParam Long pharmacyId) {
        return ResponseEntity.ok(genericStockSettingService.getDropdownSettings(pharmacyId));
    }

    @GetMapping("/{settingId}/brands")
    @Operation(summary = "Get Active Brand SKUs for Generic Group", description = "Returns active brand SKUs under a generic stock setting for Procurement brand selection.")
    public ResponseEntity<List<SkuResponseDto>> getActiveBrands(@PathVariable Long settingId) {
        return ResponseEntity.ok(genericStockSettingService.getActiveBrandsBySetting(settingId));
    }

    @PutMapping("/{settingId}")
    @Operation(summary = "Update Shared Generic Stock Thresholds")
    public ResponseEntity<GenericStockGroupDto> updateThresholds(
            @PathVariable Long settingId,
            @Valid @RequestBody GenericStockSettingRequestDto request) {
        return ResponseEntity.ok(genericStockSettingService.updateThresholds(settingId, request));
    }

    @PutMapping
    @Operation(summary = "Upsert Shared Generic Stock Thresholds")
    public ResponseEntity<GenericStockGroupDto> upsertThresholds(
            @Valid @RequestBody GenericStockSettingRequestDto request) {
        return ResponseEntity.ok(genericStockSettingService.upsertThresholds(request));
    }
}
