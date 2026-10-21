package com.example.backend.controller;

import com.example.backend.dto.order.OrderRequestDto;
import com.example.backend.dto.order.OrderResponseDto;
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

    // 2. GET ALL ORDERS
    @GetMapping
    @Operation(summary = "Get All Orders by Facility", description = "Retrieves purchase orders for a facility with optional status filtering (Pending, Approved, Denied, Partially_Received, Received).")
    public ResponseEntity<List<OrderResponseDto>> getAllOrders(
            @Parameter(description = "Facility ID", required = true)
            @RequestParam Long facilityId,
            @Parameter(description = "Optional order status filter (single or multiple, e.g. Approved, Partially_Received)")
            @RequestParam(required = false) List<Order.Status> status) {
        return ResponseEntity.ok(orderService.getAllOrders(facilityId, status));
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
