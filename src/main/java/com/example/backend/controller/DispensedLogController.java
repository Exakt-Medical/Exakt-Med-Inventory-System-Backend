package com.example.backend.controller;

import com.example.backend.dto.dispensed.DispensedLogResponseDto;
import com.example.backend.entity.DispensedLog;
import com.example.backend.service.DispensedLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dispensed-logs")
@Tag(name = "Dispensed Logs", description = "Endpoints to manage and track dispensed medicine logs and recipient status")
public class DispensedLogController {

    private final DispensedLogService dispensedLogService;

    public DispensedLogController(DispensedLogService dispensedLogService) {
        this.dispensedLogService = dispensedLogService;
    }

    // 1. UPDATE STATUS TO RECEIVED (BUTTON CLICK)
    @PatchMapping("/{id}/receive")
    @Operation(summary = "Mark Dispensed Log as Received", description = "Updates a dispensed log status from Pending to Received and sets received timestamp.")
    public ResponseEntity<DispensedLogResponseDto> markAsReceived(
            @Parameter(description = "Dispensed Log ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(dispensedLogService.updateStatusToReceived(id));
    }

    // 2. GET ALL DISPENSED LOGS
    @GetMapping
    @Operation(summary = "Get All Dispensed Logs", description = "Retrieves dispensed logs optionally filtered by facility and status (Pending, Received).")
    public ResponseEntity<List<DispensedLogResponseDto>> getAllDispensedLogs(
            @Parameter(description = "Optional facility ID filter")
            @RequestParam(required = false) Long facilityId,
            @Parameter(description = "Optional status filter (Pending, Received)")
            @RequestParam(required = false) DispensedLog.Status status) {
        return ResponseEntity.ok(dispensedLogService.getAllDispensedLogs(facilityId, status));
    }

    // 3. GET DISPENSED LOG BY ID
    @GetMapping("/{id}")
    @Operation(summary = "Get Dispensed Log by ID", description = "Retrieves details of a specific dispensed log.")
    public ResponseEntity<DispensedLogResponseDto> getDispensedLogById(
            @Parameter(description = "Dispensed Log ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(dispensedLogService.getDispensedLogById(id));
    }
}
