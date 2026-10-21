package com.example.backend.dto.sku;

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
public class SkuDropdownDto {
    private Long id;
    private String sku;
    private String brandName;
    private String genericName;
    private String dosage;
    private Long currentStock;
    private Long maximumLevel;
    private Boolean hasPendingRestock;
    private Long pendingRestockUnits;
    private String pendingRestockStatus;
}
