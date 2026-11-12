package com.example.backend.dto.reorderQuotation;

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
public class ReorderQuotationSummaryDto {
    private long totalQuotations;
    private long pendingCount;
    private long approvedCount;
    private long rejectedCount;
    private long cancelledCount;
}

