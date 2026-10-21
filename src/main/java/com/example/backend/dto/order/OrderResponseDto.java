package com.example.backend.dto.order;

import com.example.backend.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponseDto {

    private Long id;
    private String purchaseOrderNum;
    private String poNumberFormatted;
    private Long facilityId;
    private String facilityName;
    private Long supplierId;
    private String supplierName;
    private String priority;
    private Long totalOrderedUnits;
    private Long totalReceivedUnits;
    private Long totalPrice;
    private String notes;
    private Order.Status status;
    private List<OrderItemResponseDto> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message;
}
