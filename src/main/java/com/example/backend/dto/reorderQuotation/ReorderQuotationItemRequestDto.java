package com.example.backend.dto.reorderQuotation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public class ReorderQuotationItemRequestDto {

    // Generic-first: preferred identifier when Pharmacist requests a generic medicine
    private Long genericStockSettingId;

    // Optional fallback / legacy: if skuId is provided without genericStockSettingId, backend resolves setting from SKU
    private Long skuId;

    @NotNull(message = "Requested units is required")
    @Min(value = 1, message = "Requested units must be at least 1")
    private Long requestedUnits;

    @Builder.Default
    private Long estimatedPrice = 0L;

    private String notes;
}
