package com.example.backend.dto.sku;

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
public class SkuRequestDto {

    @NotNull(message = "Facility ID is required")
    private Long facilityId;

    @NotNull(message = "Medicine ID is required")
    private Long medicineId;

    private String name;

    @NotBlank(message = "Brand name is required")
    private String brandName;

    private String dosageForm;

    private String dosageStrength;

    private String packagingUnit;

    private String packagingUnitCode;

    @NotNull(message = "Minimum level is required")
    private Long minimumLevel;

    @NotNull(message = "Reorder level is required")
    private Long reorderLevel;

    @NotNull(message = "Maximum level is required")
    private Long maximumLevel;
}
