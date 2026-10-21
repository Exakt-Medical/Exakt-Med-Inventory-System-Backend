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
public class SkuSummaryDto {
    private long totalSkus;
    private long optimalCount;
    private long reorderCount;
    private long criticalCount;
}
