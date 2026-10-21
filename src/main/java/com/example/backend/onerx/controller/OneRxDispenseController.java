package com.example.backend.onerx.controller;

import com.example.backend.onerx.dto.OneRxDispenseRequestDto;
import com.example.backend.onerx.dto.OneRxDispenseResponseDto;
import com.example.backend.onerx.service.OneRxDispenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dispense")
@Tag(name = "OneRx Integration - Dispense Webhook", description = "Endpoints for OneRx system integration to record dispensing and automatically decrement inventory using FEFO.")
public class OneRxDispenseController {

    private final OneRxDispenseService oneRxDispenseService;

    public OneRxDispenseController(OneRxDispenseService oneRxDispenseService) {
        this.oneRxDispenseService = oneRxDispenseService;
    }

    /**
     * POST /api/dispense
     * Also accessible via /api/onerx/dispense
     *
     * Headers:
     * Authorization: Bearer <token>
     * Content-Type: application/json
     * Accept: application/json
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Dispense medications from OneRx",
            description = "Receives dispensed medication event from OneRx, validates stock levels atomically, deducts inventory using FEFO, and logs immutable ledger entries."
    )
    public ResponseEntity<OneRxDispenseResponseDto> dispenseMedications(
            @Valid @RequestBody OneRxDispenseRequestDto request) {
        try {
            OneRxDispenseResponseDto response = oneRxDispenseService.dispenseMedications(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            String errorType = ex.getMessage() != null && ex.getMessage().toLowerCase().contains("insufficient stock")
                    ? "INSUFFICIENT_STOCK"
                    : "INVALID_REQUEST";

            OneRxDispenseResponseDto errorResponse = OneRxDispenseResponseDto.builder()
                    .status("failed")
                    .reason(ex.getMessage())
                    .error(errorType)
                    .build();

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception ex) {
            OneRxDispenseResponseDto errorResponse = OneRxDispenseResponseDto.builder()
                    .status("failed")
                    .reason(ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred during dispensing.")
                    .error("SERVER_ERROR")
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
