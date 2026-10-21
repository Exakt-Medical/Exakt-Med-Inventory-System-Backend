package com.example.backend.dto.inventory;

import com.example.backend.entity.InventoryTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransactionResponseDto {

    private Long id;
    private Long skuId;
    private String skuCode;
    private String brandName;
    private String genericName;
    private String dosage;
    private String packagingUnit;
    private Long facilityId;
    private String facilityName;
    private Long userId;
    private String userName;
    private String userRole;
    private String authorizedBy;
    private String performedBy;
    private InventoryTransaction.Type transactionType;
    private String typeLabel;
    private String referenceNumber;
    private String batchNum;
    private LocalDate expiryDate;
    private Long quantityIn;
    private Long quantityOut;
    private Long previousBalance;
    private Long balanceAfter;
    private String reason;
    private String notes;
    private LocalDateTime createdAt;
}
