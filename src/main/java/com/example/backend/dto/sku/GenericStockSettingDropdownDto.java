package com.example.backend.dto.sku;

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
public class GenericStockSettingDropdownDto {
    private Long settingId;
    private Long medicineId;
    private String drugCode;
    private String genericName;
    private String dosageForm;
    private String dosageStrength;
    private String packagingUnit;
    private Long totalUnits;
    private Long minimumLevel;
    private Long reorderLevel;
    private Long maximumLevel;
    private Integer activeBrandCount;
}
