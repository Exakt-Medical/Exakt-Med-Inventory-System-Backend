package com.example.backend.dto.order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponseDto {

    private Long id;
    private Long skuId;
    private String skuName;
    private String brandName;
    private String genericName;
    private String dosageForm;
    private String packagingUnit;
    private Long orderedUnits;
    private Long receivedUnits;
    private Long price;
    private Double pricePerUnit;
}
