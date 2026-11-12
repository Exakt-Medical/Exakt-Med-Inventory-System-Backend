package com.example.backend.dto.reorderQuotation;

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
public class ReorderQuotationItemResponseDto {

    private Long id;
    private Long skuId;
    private String skuCode;
    private String brandName;
    private String genericName;
    private String dosageForm;
    private String dosageStrength;
    private String packagingUnit;
    private Long currentStock;
    private Long requestedUnits;
    private Long estimatedPrice;
    private Double estimatedPricePerUnit;
    private String notes;
}

