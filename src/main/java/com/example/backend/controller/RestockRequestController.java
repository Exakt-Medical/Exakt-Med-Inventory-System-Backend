package com.example.backend.controller;

import com.example.backend.dto.restockRequest.RestockRequestDto;
import com.example.backend.dto.restockRequest.RestockResponseDto;
import com.example.backend.service.RestockRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restock-requests")
@Tag(name = "Restock Requests", description = "Endpoints for pharmacists to flag low inventory and request purchase replenishment from procurement")
public class RestockRequestController {

    private final RestockRequestService restockRequestService;

    public RestockRequestController(RestockRequestService restockRequestService) {
        this.restockRequestService = restockRequestService;
    }

    // 1. CREATE RESTOCK REQUEST
    @PostMapping
    @Operation(summary = "Create Restock Request", description = "Submits a replenishment request for an SKU from a pharmacy facility.")
    public ResponseEntity<RestockResponseDto> createRestockRequest(@Valid @RequestBody RestockRequestDto request) {
        RestockResponseDto response = restockRequestService.createRestockRequest(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. GET ALL RESTOCK REQUESTS
    @GetMapping
    @Operation(summary = "Get All Restock Requests", description = "Retrieves restock requests optionally filtered by facility ID and pending status.")
    public ResponseEntity<List<RestockResponseDto>> getAllRestockRequests(
            @Parameter(description = "Optional facility ID filter")
            @RequestParam(required = false) Long facilityId,
            @Parameter(description = "Filter only pending requests")
            @RequestParam(required = false, defaultValue = "false") Boolean pendingOnly) {
        return ResponseEntity.ok(restockRequestService.getAllRestockRequests(facilityId, pendingOnly));
    }

    // 3. GET RESTOCK REQUEST BY ID
    @GetMapping("/{id}")
    @Operation(summary = "Get Restock Request by ID", description = "Retrieves details of a specific restock request.")
    public ResponseEntity<RestockResponseDto> getRestockRequestById(
            @Parameter(description = "Restock Request ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(restockRequestService.getRestockRequestById(id));
    }
}
