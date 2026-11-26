package com.example.backend.dto.sku;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenericStockGroupDto {

    private Long settingId;
    private Long pharmacyId;
    private String pharmacyName;
    private Long medicineId;
    private String drugCode;
    private String genericName;
    private String dosageForm;
    private String dosageStrength;
    private String packagingUnit;

    // Application-synced pooled total_units across all Active brands in this group
    private Long totalUnits;
    private Long minimumLevel;
    private Long reorderLevel;
    private Long maximumLevel;

    // Pooled stock health status: OPTIMAL | REORDER | CRITICAL | OUT_OF_STOCK
    private String stockStatus;

    private Integer brandCount;
    private Integer activeBrandCount;

    // Aggregated pipeline & expiry metrics across brands in group
    private Long pendingUnits;
    private Long toReceiveUnits;
    private Long expiredBatchesCount;
    private Long expiredUnits;

    private List<SkuResponseDto> brands;
}
