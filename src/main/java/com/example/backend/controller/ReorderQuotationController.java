package com.example.backend.controller;

import com.example.backend.dto.common.PageResponseDto;
import com.example.backend.dto.reorderQuotation.ConvertQuotationToOrderRequestDto;
import com.example.backend.dto.reorderQuotation.RejectQuotationRequestDto;
import com.example.backend.dto.reorderQuotation.ReorderQuotationRequestDto;
import com.example.backend.dto.reorderQuotation.ReorderQuotationResponseDto;
import com.example.backend.service.ReorderQuotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reorder-quotations")
@Tag(name = "Reorder Quotations", description = "Endpoints for creating and managing reorder quotations and converting them to purchase orders")
public class ReorderQuotationController {

    private final ReorderQuotationService quotationService;

    public ReorderQuotationController(ReorderQuotationService quotationService) {
        this.quotationService = quotationService;
    }

    // 1. CREATE REORDER QUOTATION
    @PostMapping
    @Operation(summary = "Create Reorder Quotation", description = "Pharmacist submits a new reorder quotation for a specific supplier with selected SKUs.")
    public ResponseEntity<ReorderQuotationResponseDto> createQuotation(@Valid @RequestBody ReorderQuotationRequestDto request) {
        ReorderQuotationResponseDto response = quotationService.createQuotation(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. GET QUOTATIONS PAGINATED
    @GetMapping
    @Operation(summary = "Get Reorder Quotations Paginated", description = "Retrieves paginated quotations filtered by pharmacy, supplier, status, priority, and search.")
    public ResponseEntity<PageResponseDto<ReorderQuotationResponseDto>> getQuotationsPaginated(
            @Parameter(description = "Pharmacy ID", required = true)
            @RequestParam Long pharmacyId,
            @Parameter(description = "Optional Supplier ID filter")
            @RequestParam(required = false) Long supplierId,
            @Parameter(description = "Optional status filter (e.g. ALL, Pending, Approved, Rejected, Cancelled)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Optional priority filter (e.g. ALL, Normal, Urgent)")
            @RequestParam(required = false) String priority,
            @Parameter(description = "Optional search query")
            @RequestParam(required = false) String search,
            @Parameter(description = "Page index (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort property (createdAt, quotationNumber, totalUnits, etc.)")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (ASC, DESC)")
            @RequestParam(defaultValue = "DESC") String sortDir) {
        return ResponseEntity.ok(quotationService.getQuotationsPaginated(
                pharmacyId, supplierId, status, priority, search, page, size, sortBy, sortDir));
    }

    // 3. GET QUOTATION BY ID
    @GetMapping("/{id}")
    @Operation(summary = "Get Reorder Quotation by ID", description = "Retrieves full details of a single quotation including all line items.")
    public ResponseEntity<ReorderQuotationResponseDto> getQuotationById(@PathVariable Long id) {
        return ResponseEntity.ok(quotationService.getQuotationById(id));
    }

    // 4. UPDATE REORDER QUOTATION
    @PutMapping("/{id}")
    @Operation(summary = "Update Reorder Quotation", description = "Allows updating line items, supplier, notes, or priority while status is Pending.")
    public ResponseEntity<ReorderQuotationResponseDto> updateQuotation(
            @PathVariable Long id,
            @Valid @RequestBody ReorderQuotationRequestDto request) {
        return ResponseEntity.ok(quotationService.updateQuotation(id, request));
    }

    // 5. CONVERT QUOTATION TO PURCHASE ORDER
    @PostMapping("/{id}/convert-to-order")
    @Operation(summary = "Convert Quotation to Purchase Order", description = "Procurement reviews the quotation, optionally adjusts items/prices, and converts it to a Purchase Order with status Pending.")
    public ResponseEntity<ReorderQuotationResponseDto> convertQuotationToOrder(
            @PathVariable Long id,
            @RequestBody(required = false) ConvertQuotationToOrderRequestDto convertRequest) {
        ReorderQuotationResponseDto response = quotationService.convertQuotationToOrder(id, convertRequest);
        return ResponseEntity.ok(response);
    }

    // 6. REJECT QUOTATION
    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject Reorder Quotation", description = "Procurement rejects the quotation with a clinical or commercial reason.")
    public ResponseEntity<ReorderQuotationResponseDto> rejectQuotation(
            @PathVariable Long id,
            @Valid @RequestBody RejectQuotationRequestDto rejectRequest) {
        return ResponseEntity.ok(quotationService.rejectQuotation(id, rejectRequest));
    }

    // 7. CANCEL QUOTATION
    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel Reorder Quotation", description = "Cancels a pending quotation.")
    public ResponseEntity<ReorderQuotationResponseDto> cancelQuotation(@PathVariable Long id) {
        return ResponseEntity.ok(quotationService.cancelQuotation(id));
    }
}

