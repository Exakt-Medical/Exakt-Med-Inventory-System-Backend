package com.example.backend.dto.sku;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GenericStockSettingRequestDto {

    private Long pharmacyId;
    private Long medicineId;
    private String dosageForm;
    private String dosageStrength;
    private String packagingUnit;

    @NotNull(message = "Minimum level is required")
    @Min(value = 0, message = "Minimum level cannot be negative")
    private Long minimumLevel;

    @NotNull(message = "Reorder level is required")
    @Min(value = 1, message = "Reorder level must be at least 1")
    private Long reorderLevel;

    @NotNull(message = "Maximum level is required")
    @Min(value = 2, message = "Maximum level must be at least 2")
    private Long maximumLevel;
}
