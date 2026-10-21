package com.example.backend.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryLedgerHeaderDto {

    private Long skuId;
    private String name;
    private String brandName;
    private String genericName;
    private String dosageForm;
    private String dosage;
    private String packagingUnit;
    private Long minimumLevel;
    private Long reorderLevel;
    private Long maximumLevel;
    private Long currentUnits;
    private String stockStatus;
    private Long totalReceived;
    private Long totalDeducted;
}
