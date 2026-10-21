package com.example.backend.dto.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchSummaryDto {
    private long totalBatches;
    private long totalActiveStock;
    private long expiryAlertCount;
    private long quarantinedCount;
}
