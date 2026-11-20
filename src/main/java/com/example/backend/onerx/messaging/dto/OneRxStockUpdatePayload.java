package com.example.backend.onerx.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneRxStockUpdatePayload {

    private String eventId;
    private String sourceSystem;
    private String eventType;
    private String pharmacyCode;
    private String timestamp;
    private List<StockItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockItemDto {
        private String drugCode;
        private String drugName;
        private String brandName;
        private String skuName;
        private Integer availableQuantity;
        private String packagingUnit;
        private String dosageForm;
        private String dosageStrength;
    }
}
