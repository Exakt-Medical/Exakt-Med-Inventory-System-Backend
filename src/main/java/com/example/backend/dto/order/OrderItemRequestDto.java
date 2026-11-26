package com.example.backend.dto.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRequestDto {

    // Optional: links back to ReorderQuotationItem when converting a generic quotation to a PO
    private Long quotationItemId;

    @NotNull(message = "SKU ID is required")
    private Long skuId;

    @NotNull(message = "Ordered units is required")
    @Positive(message = "Ordered units must be greater than 0")
    private Long orderedUnits;

    @NotNull(message = "Price is required")
    @PositiveOrZero(message = "Price must be greater than or equal to 0")
    private Long price;
}
