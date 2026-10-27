package com.example.backend.controller;

import com.example.backend.dto.common.PageResponseDto;
import com.example.backend.dto.order.OrderRequestDto;
import com.example.backend.dto.order.OrderResponseDto;
import com.example.backend.dto.order.OrderSummaryDto;
import com.example.backend.entity.Order;
import com.example.backend.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders & Procurement", description = "Endpoints for creating purchase orders, reviewing order requests, and updating approval statuses")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 1. CREATE PURCHASE ORDER REQUEST
    @PostMapping
    @Operation(summary = "Create Purchase Order", description = "Submits a new purchase order with one or more ordered items, payment terms, and delivery instructions.")
    public ResponseEntity<OrderResponseDto> createOrder(@Valid @RequestBody OrderRequestDto request) {
        OrderResponseDto response = orderService.createOrder(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. GET ORDERS PAGINATED (Server-side paginated search, status, and priority filtering)
    @GetMapping
    @Operation(summary = "Get Orders Paginated", description = "Retrieves paginated purchase orders with search, status, and priority filters.")
    public ResponseEntity<PageResponseDto<OrderResponseDto>> getOrdersPaginated(
            @Parameter(description = "Facility ID", required = true)
            @RequestParam Long facilityId,
            @Parameter(description = "Optional search query")
            @RequestParam(required = false) String search,
            @Parameter(description = "Optional status filter (e.g. ALL, Pending, Approved, Partially_Received, Received, Denied)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Optional priority filter (e.g. ALL, Normal, Urgent)")
            @RequestParam(required = false) String priority,
            @Parameter(description = "Page index (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort property (createdAt, purchaseOrderNum, totalPrice, etc.)")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (ASC, DESC)")
            @RequestParam(defaultValue = "DESC") String sortDir) {
        return ResponseEntity.ok(orderService.getOrdersPaginated(
                facilityId, search, status, priority, page, size, sortBy, sortDir));
    }

    // 2b. GET ORDER SUMMARY KPIS
    @GetMapping("/summary")
    @Operation(summary = "Get Order Summary KPIs", description = "Returns aggregated status breakdown counts for the facility.")
    public ResponseEntity<OrderSummaryDto> getOrderSummary(
            @Parameter(description = "Facility ID", required = true)
            @RequestParam Long facilityId) {
        return ResponseEntity.ok(orderService.getOrderSummary(facilityId));
    }

    // 2c. GET RECEIVABLE ORDERS
    @GetMapping("/receivable")
    @Operation(summary = "Get Receivable Orders", description = "Returns active Approved and Partially_Received purchase orders for batch intake.")
    public ResponseEntity<List<OrderResponseDto>> getReceivableOrders(
            @Parameter(description = "Facility ID", required = true)
            @RequestParam Long facilityId) {
        return ResponseEntity.ok(orderService.getReceivableOrders(facilityId));
    }

    // 3. GET ORDER BY ID
    @GetMapping("/{id}")
    @Operation(summary = "Get Order by ID", description = "Retrieves the complete purchase order details including ordered item line items.")
    public ResponseEntity<OrderResponseDto> getOrderById(
            @Parameter(description = "Order ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    // 4. UPDATE ORDER STATUS
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update Order Status", description = "Updates order status to Approved, Denied, etc., with audit notes.")
    public ResponseEntity<OrderResponseDto> updateOrderStatus(
            @Parameter(description = "Order ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Target status (Pending, Approved, Denied)", required = true)
            @RequestParam Order.Status status,
            @Parameter(description = "Optional review notes or reason for status")
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, status, notes));
    }
}
