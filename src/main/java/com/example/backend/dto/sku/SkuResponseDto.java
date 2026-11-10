package com.example.backend.dto.sku;

import com.example.backend.entity.Sku;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SkuResponseDto {

    private Long id;
    private Long pharmacyId;
    private String pharmacyName;
    private Long medicineId;
    private String drugCode;
    private String drugDescription;
    private String name;
    private String brandName;
    private String dosageForm;
    private String dosageStrength;
    private String packagingUnit;
    private Long units;
    private Long minimumLevel;
    private Long reorderLevel;
    private Long maximumLevel;
    private Sku.Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message;

    // Restock & Order Pipeline tracking
    private Long pendingUnits;
    private Long toReceiveUnits;
    private Boolean hasPendingRestock;
    private Long pendingRestockUnits;
    private Long pendingRestockRequestId;
    private LocalDateTime pendingRestockCreatedAt;
    private String pendingRestockStatus;

    // Expired Batches tracking
    private Long expiredBatchesCount;
    private Long expiredUnits;
}
