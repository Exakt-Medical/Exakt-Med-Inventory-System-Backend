package com.example.backend.dto.sku;

import com.example.backend.entity.Sku;
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

    @NotNull(message = "Pharmacy ID is required")
    private Long pharmacyId;

    @NotNull(message = "Medicine ID is required")
    private Long medicineId;

    private String name;

    @NotBlank(message = "Brand name is required")
    private String brandName;

    private String dosageForm;

    private String dosageStrength;

    private String packagingUnit;

    private String packagingUnitCode;

    private Long minimumLevel;

    private Long reorderLevel;

    private Long maximumLevel;

    private Sku.Status status;
}
