package com.example.backend.dto.reorderQuotation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class ReorderQuotationRequestDto {

    @NotNull(message = "Pharmacy ID is required")
    private Long pharmacyId;

    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    @Builder.Default
    private String priority = "Normal";

    private String notes;

    @NotEmpty(message = "Quotation must contain at least one medicine item")
    @Valid
    private List<ReorderQuotationItemRequestDto> items;
}

