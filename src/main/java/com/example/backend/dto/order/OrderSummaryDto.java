package com.example.backend.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryDto {
    private long totalOrders;
    private long pendingCount;
    private long approvedCount;
    private long partiallyReceivedCount;
    private long receivedCount;
    private long deniedCount;
}
