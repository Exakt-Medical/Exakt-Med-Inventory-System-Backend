package com.example.backend.service;

import com.example.backend.dto.order.OrderItemRequestDto;
import com.example.backend.dto.order.OrderItemResponseDto;
import com.example.backend.dto.order.OrderRequestDto;
import com.example.backend.dto.order.OrderResponseDto;
import com.example.backend.entity.Facility;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderedItem;
import com.example.backend.entity.Sku;
import com.example.backend.entity.Supplier;
import com.example.backend.entity.RestockRequest;
import com.example.backend.entity.AuditLog;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.OrderedItemRepository;
import com.example.backend.repository.RestockRequestRepository;
import com.example.backend.repository.SkuRepository;
import com.example.backend.repository.SupplierRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final SupplierRepository supplierRepository;
    private final SkuRepository skuRepository;
    private final FacilityRepository facilityRepository;
    private final RestockRequestRepository restockRequestRepository;
    private final AuditLogService auditLogService;

    public OrderService(OrderRepository orderRepository,
                        OrderedItemRepository orderedItemRepository,
                        SupplierRepository supplierRepository,
                        SkuRepository skuRepository,
                        FacilityRepository facilityRepository,
                        RestockRequestRepository restockRequestRepository,
                        AuditLogService auditLogService) {
        this.orderRepository = orderRepository;
        this.orderedItemRepository = orderedItemRepository;
        this.supplierRepository = supplierRepository;
        this.skuRepository = skuRepository;
        this.facilityRepository = facilityRepository;
        this.restockRequestRepository = restockRequestRepository;
        this.auditLogService = auditLogService;
    }

    // 1. CREATE PURCHASE ORDER REQUEST
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Procurement')")
    public OrderResponseDto createOrder(OrderRequestDto request) {
        if (request.getFacilityId() == null) {
            throw new RuntimeException("Facility ID is required");
        }

        Facility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new RuntimeException("Facility not found with id: " + request.getFacilityId()));

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + request.getSupplierId()));

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Purchase order must contain at least one line item.");
        }

        long totalUnits = request.getItems().stream()
                .mapToLong(i -> i.getOrderedUnits() != null ? i.getOrderedUnits() : 0L)
                .sum();

        // Calculate totalPrice by summing the price of each ordered item
        long totalPrice = request.getItems().stream()
                .mapToLong(i -> i.getPrice() != null ? i.getPrice() : 0L)
                .sum();

        LocalDateTime now = LocalDateTime.now();

        Order order = new Order();
        order.setFacility(facility);
        order.setSupplier(supplier);
        order.setPurchaseOrderNum("PO-PENDING");
        order.setPriority(request.getPriority() != null && !request.getPriority().isBlank()
                ? request.getPriority()
                : "Normal");
        order.setTotalOrderedUnits(totalUnits);
        order.setTotalPrice(totalPrice);
        order.setNotes(request.getNotes());
        // Status explicitly defaults to Pending
        order.setStatus(Order.Status.Pending);

        Order savedOrder = orderRepository.saveAndFlush(order);

        // PO number format: PO-[current year]-[month]-[purchase order id], e.g. PO-2026-09-0001
        String formattedPoNumber = String.format("PO-%d-%02d-%04d",
                now.getYear(),
                now.getMonthValue(),
                savedOrder.getId());
        savedOrder.setPurchaseOrderNum(formattedPoNumber);
        savedOrder = orderRepository.save(savedOrder);

        List<OrderedItem> orderedItems = new ArrayList<>();
        List<OrderItemResponseDto> itemResponses = new ArrayList<>();

        for (OrderItemRequestDto itemDto : request.getItems()) {
            Sku sku = skuRepository.findById(itemDto.getSkuId())
                    .orElseThrow(() -> new RuntimeException("SKU not found with id: " + itemDto.getSkuId()));

            OrderedItem orderedItem = new OrderedItem();
            orderedItem.setOrder(savedOrder);
            orderedItem.setSku(sku);
            orderedItem.setOrderedUnits(itemDto.getOrderedUnits());
            orderedItem.setReceivedUnits(0L);
            long itemPrice = itemDto.getPrice() != null ? itemDto.getPrice() : 0L;
            long units = itemDto.getOrderedUnits() != null ? itemDto.getOrderedUnits() : 0L;
            orderedItem.setPrice(itemPrice);

            double calculatedPricePerUnit = units > 0
                    ? Math.round(((double) itemPrice / units) * 100.0) / 100.0
                    : 0.0;
            orderedItem.setPricePerUnit(calculatedPricePerUnit);

            orderedItems.add(orderedItem);
        }

        List<OrderedItem> savedItems = orderedItemRepository.saveAll(orderedItems);
        for (OrderedItem savedItem : savedItems) {
            itemResponses.add(mapToItemResponseDto(savedItem));
        }

        // If this purchase order fulfills Pharmacist Restock Requests, link them to the Order and matching OrderedItem
        if (request.getRestockRequestIds() != null && !request.getRestockRequestIds().isEmpty()) {
            final Order orderForRestock = savedOrder;
            for (Long rId : request.getRestockRequestIds()) {
                if (rId == null) continue;
                restockRequestRepository.findById(rId).ifPresent(rr -> {
                    rr.setOrder(orderForRestock);
                    if (rr.getSku() != null) {
                        savedItems.stream()
                                .filter(si -> si.getSku() != null && si.getSku().getId().equals(rr.getSku().getId()))
                                .findFirst()
                                .ifPresent(rr::setOrderedItem);
                    }
                    restockRequestRepository.save(rr);
                });
            }
        }

        auditLogService.logAction(
                facility,
                "Purchasing",
                "PURCHASE_ORDER_CREATED",
                "Purchase Order Created (" + savedOrder.getPurchaseOrderNum() + ")",
                AuditLog.Severity.INFO,
                savedOrder.getPurchaseOrderNum(),
                savedOrder.getId(),
                "Created purchase order requisition " + savedOrder.getPurchaseOrderNum() + " (" + savedOrder.getTotalOrderedUnits() + " units) for vendor '" + (supplier != null ? supplier.getName() : "") + "'. Total: ₱" + savedOrder.getTotalPrice() + ".",
                "Admin,Procurement"
        );

        OrderResponseDto response = mapToOrderResponseDto(savedOrder, itemResponses);
        response.setMessage("Purchase order request created successfully.");
        return response;
    }

    // 2. GET ALL ORDERS (facilityId is strictly required, optional status or list of statuses filter)
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Procurement', 'Pharmacist')")
    public List<OrderResponseDto> getAllOrders(Long facilityId, List<Order.Status> statuses) {
        if (facilityId == null) {
            throw new RuntimeException("Facility ID is required");
        }

        List<Order> orders;
        if (statuses != null && !statuses.isEmpty()) {
            if (statuses.size() == 1) {
                orders = orderRepository.findByFacilityIdAndStatusOrderByCreatedAtDesc(facilityId, statuses.get(0));
            } else {
                orders = orderRepository.findByFacilityIdAndStatusInOrderByCreatedAtDesc(facilityId, statuses);
            }
        } else {
            orders = orderRepository.findByFacilityIdOrderByCreatedAtDesc(facilityId);
        }

        return orders.stream()
                .map(order -> {
                    List<OrderedItem> items = orderedItemRepository.findByOrderId(order.getId());
                    List<OrderItemResponseDto> itemDtos = items.stream()
                            .map(this::mapToItemResponseDto)
                            .collect(Collectors.toList());
                    return mapToOrderResponseDto(order, itemDtos);
                })
                .collect(Collectors.toList());
    }

    // 3. GET ORDER BY ID
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Procurement', 'Pharmacist')")
    public OrderResponseDto getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

        List<OrderedItem> items = orderedItemRepository.findByOrderId(order.getId());
        List<OrderItemResponseDto> itemDtos = items.stream()
                .map(this::mapToItemResponseDto)
                .collect(Collectors.toList());

        return mapToOrderResponseDto(order, itemDtos);
    }

    // 4. UPDATE ORDER STATUS
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public OrderResponseDto updateOrderStatus(Long id, Order.Status status, String notes) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

        Order.Status newStatus = status != null ? status : Order.Status.Pending;
        order.setStatus(newStatus);

        if (newStatus == Order.Status.Denied) {
            if (notes == null || notes.trim().isEmpty()) {
                throw new RuntimeException("Notes are required when status is Denied.");
            }
            order.setNotes(notes.trim());
        } else if (newStatus == Order.Status.Approved) {
            order.setNotes(null);
        } else if (notes != null) {
            order.setNotes(notes.trim());
        }

        Order updatedOrder = orderRepository.save(order);

        AuditLog.Severity severity = newStatus == Order.Status.Approved
                ? AuditLog.Severity.SUCCESS
                : (newStatus == Order.Status.Denied ? AuditLog.Severity.WARNING : AuditLog.Severity.INFO);

        auditLogService.logAction(
                updatedOrder.getFacility(),
                "Purchasing",
                "PURCHASE_ORDER_" + newStatus.name().toUpperCase(),
                "Purchase Order " + newStatus + " (" + updatedOrder.getPurchaseOrderNum() + ")",
                severity,
                updatedOrder.getPurchaseOrderNum(),
                updatedOrder.getId(),
                "Purchase order " + updatedOrder.getPurchaseOrderNum() + " status transitioned to " + newStatus + "." + (notes != null && !notes.isBlank() ? " Notes: " + notes : ""),
                "Admin,Procurement"
        );

        List<OrderedItem> items = orderedItemRepository.findByOrderId(updatedOrder.getId());
        List<OrderItemResponseDto> itemDtos = items.stream()
                .map(this::mapToItemResponseDto)
                .collect(Collectors.toList());

        OrderResponseDto response = mapToOrderResponseDto(updatedOrder, itemDtos);
        response.setMessage("Order status updated successfully.");
        return response;
    }

    private OrderItemResponseDto mapToItemResponseDto(OrderedItem item) {
        OrderItemResponseDto dto = new OrderItemResponseDto();
        dto.setId(item.getId());
        if (item.getSku() != null) {
            dto.setSkuId(item.getSku().getId());
            dto.setSkuName(item.getSku().getName());
            dto.setBrandName(item.getSku().getBrandName());
            dto.setDosageForm(item.getSku().getDosageForm());
            dto.setPackagingUnit(item.getSku().getPackagingUnit());
            if (item.getSku().getLibMedicine() != null) {
                dto.setGenericName(item.getSku().getLibMedicine().getDrugDescription());
            }
        }
        dto.setOrderedUnits(item.getOrderedUnits());
        dto.setReceivedUnits(item.getReceivedUnits() != null ? item.getReceivedUnits() : 0L);
        dto.setPrice(item.getPrice());

        Double unitPrice = item.getPricePerUnit();
        if (unitPrice == null && item.getOrderedUnits() != null && item.getOrderedUnits() > 0 && item.getPrice() != null) {
            unitPrice = Math.round(((double) item.getPrice() / item.getOrderedUnits()) * 100.0) / 100.0;
        }
        dto.setPricePerUnit(unitPrice != null ? unitPrice : 0.0);
        return dto;
    }

    private OrderResponseDto mapToOrderResponseDto(Order order, List<OrderItemResponseDto> items) {
        OrderResponseDto dto = new OrderResponseDto();
        dto.setId(order.getId());
        dto.setPurchaseOrderNum(order.getPurchaseOrderNum());
        dto.setPoNumberFormatted(order.getPurchaseOrderNum());
        if (order.getFacility() != null) {
            dto.setFacilityId(order.getFacility().getId());
            dto.setFacilityName(order.getFacility().getName());
        }
        if (order.getSupplier() != null) {
            dto.setSupplierId(order.getSupplier().getId());
            dto.setSupplierName(order.getSupplier().getName());
        }
        dto.setPriority(order.getPriority());
        dto.setTotalOrderedUnits(order.getTotalOrderedUnits());
        long totalReceived = (items != null)
                ? items.stream().mapToLong(i -> i.getReceivedUnits() != null ? i.getReceivedUnits() : 0L).sum()
                : 0L;
        dto.setTotalReceivedUnits(totalReceived);
        dto.setTotalPrice(order.getTotalPrice());
        dto.setNotes(order.getNotes());
        dto.setStatus(order.getStatus());
        dto.setItems(items);
        dto.setCreatedAt(order.getCreatedAt());
        dto.setUpdatedAt(order.getUpdatedAt());
        return dto;
    }
}
