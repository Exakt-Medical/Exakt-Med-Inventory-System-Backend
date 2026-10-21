package com.example.backend.controller;

import com.example.backend.dto.audit.AuditLogResponseDto;
import com.example.backend.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit Logs", description = "Endpoints for reviewing facility activity logs, user transactions, and security audit trails")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @Operation(summary = "Get Audit Logs", description = "Queries system and inventory audit logs by facility with optional module, severity, and text search filters.")
    public ResponseEntity<List<AuditLogResponseDto>> getAuditLogs(
            @Parameter(description = "Facility ID", required = true)
            @RequestParam Long facilityId,
            @Parameter(description = "Module filter (e.g. INVENTORY, ORDERS, BATCHES, USERS)")
            @RequestParam(required = false) String module,
            @Parameter(description = "Severity filter (e.g. INFO, WARN, CRITICAL)")
            @RequestParam(required = false) String severity,
            @Parameter(description = "Keyword search across action and details")
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(auditLogService.getAuditLogs(facilityId, module, severity, search));
    }
}
