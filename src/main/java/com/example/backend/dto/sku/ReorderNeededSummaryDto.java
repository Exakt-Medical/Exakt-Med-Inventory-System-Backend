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
public class ReorderNeededSummaryDto {
    private long totalReorderCount;
    private long criticalCount;
    private long reorderOnlyCount;
}

