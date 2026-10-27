package com.example.backend.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransactionSummaryDto {
    private long totalLogs;
    private long netDelta;
    private long additionsUnits;
    private long additionsCount;
    private long deductionsUnits;
    private long deductionsCount;
}
