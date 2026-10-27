package com.example.backend.dto.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogSummaryDto {
    private long totalLogs;
    private long criticalCount;
    private long warningCount;
    private long infoCount;
    private long successCount;
}
