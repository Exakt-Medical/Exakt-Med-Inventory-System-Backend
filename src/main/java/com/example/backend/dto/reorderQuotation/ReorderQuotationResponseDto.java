package com.example.backend.dto.reorderQuotation;

import com.example.backend.entity.ReorderQuotation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReorderQuotationResponseDto {

    private Long id;
    private String quotationNumber;
    private Long pharmacyId;
    private String pharmacyName;
    private Long supplierId;
    private String supplierName;
    private Long userId;
    private String userName;
    private Long orderId;
    private String purchaseOrderNum;
    private String purchaseOrderStatus;
    private String priority;
    private ReorderQuotation.Status status;
    private Long totalUnits;
    private Long estimatedTotalCost;
    private String notes;
    private String rejectionReason;
    private List<ReorderQuotationItemResponseDto> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message;
}

