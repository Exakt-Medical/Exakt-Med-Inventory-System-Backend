package com.example.backend.dto.sku;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SkuStockAdjustmentDto {

    public enum Type {
        ADD,
        SUBTRACT
    }

    @NotNull(message = "Adjustment type is required (ADD, SUBTRACT)")
    private Type type;

    @NotNull(message = "Adjustment amount is required")
    @Min(value = 0, message = "Amount must be zero or positive")
    private Long amount;

    @NotBlank(message = "Adjustment reason is required")
    private String reason;

    private String notes;

    private Long batchId;

    private String patientName;

    private String contactNumber;
}
