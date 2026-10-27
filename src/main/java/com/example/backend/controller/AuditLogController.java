package com.example.backend.controller;

import com.example.backend.dto.audit.AuditLogResponseDto;
import com.example.backend.dto.audit.AuditLogSummaryDto;
import com.example.backend.dto.common.PageResponseDto;
import com.example.backend.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit Logs", description = "Endpoints for reviewing facility activity logs, user transactions, and security audit trails")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @Operation(summary = "Get Audit Logs (Paginated)", description = "Queries system and inventory audit logs by facility with pagination, optional module, severity, and text search filters.")
    public ResponseEntity<PageResponseDto<AuditLogResponseDto>> getAuditLogs(
            @Parameter(description = "Facility ID", required = true)
            @RequestParam Long facilityId,
            @Parameter(description = "Module filter (e.g. INVENTORY, ORDERS, BATCHES, USERS)")
            @RequestParam(required = false) String module,
            @Parameter(description = "Severity filter (e.g. INFO, WARN, CRITICAL)")
            @RequestParam(required = false) String severity,
            @Parameter(description = "Keyword search across action, user, and description")
            @RequestParam(required = false) String search,
            @Parameter(description = "Page index (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort property (createdAt, userName, action, module)")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (ASC, DESC)")
            @RequestParam(defaultValue = "DESC") String sortDir) {
        return ResponseEntity.ok(auditLogService.getAuditLogs(facilityId, module, severity, search, page, size, sortBy, sortDir));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get Audit Log Summary KPIs", description = "Returns aggregated facility-level audit log counts by severity for KPI overview cards.")
    public ResponseEntity<AuditLogSummaryDto> getAuditLogSummary(
            @Parameter(description = "Facility ID", required = true)
            @RequestParam Long facilityId) {
        return ResponseEntity.ok(auditLogService.getAuditLogSummary(facilityId));
    }
}
