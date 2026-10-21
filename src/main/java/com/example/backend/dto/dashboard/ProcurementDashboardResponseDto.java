package com.example.backend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcurementDashboardResponseDto {

    private Long facilityId;
    private String facilityName;
    private Metrics metrics;
    private List<PriorityRestockSku> priorityRestockSkus;
    private List<RecentOrderRequest> recentOrderRequests;

    // --- NESTED DTOs ---

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metrics {
        private Long totalReorder;
        private Long totalMinimumSkus;
        private Long totalBatches;
        private Long totalPendingOrders;
        private Long activeSuppliers;
        private Long totalUnitsOrderedMonth;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriorityRestockSku {
        private Long id;
        private String sku;
        private String brandName;
        private String genericName;
        private String dosage;
        private Long currentStock;
        private Long minimumLevel;
        private Long reorderLevel;
        private String urgency; // "CRITICAL", "REORDER"
        private String suggestedSupplier;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentOrderRequest {
        private String orderId;
        private String sku;
        private String brandName;
        private String supplier;
        private Long quantity;
        private String priority;
        private String status;
        private String destination;
        private String requestDate;
    }
}
