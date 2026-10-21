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
public class InventorySkuSummaryDto {

    private Long id;
    private String sku;
    private String name;
    private String brandName;
    private String genericName;
    private String dosage;
    private Long currentStock;
    private String packagingUnit;
}
