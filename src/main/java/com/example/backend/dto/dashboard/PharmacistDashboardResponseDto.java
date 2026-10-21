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
public class PharmacistDashboardResponseDto {

    private Long facilityId;
    private String facilityName;
    private Metrics metrics;
    private List<CategoryStockDistribution> skuStockDistribution;
    private List<CategoryHealthBar> skuHealthBarData;
    private List<UrgentAlert> urgentAlerts;

    // --- NESTED DTOs ---

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metrics {
        private Long totalSkus;
        private Long totalStockQuantity;
        private Long totalLowStock;
        private Long totalOverStock;
        private Long totalExpiry;
        private Long totalNearExpiry;
        private Long totalBatches;
        private Long totalQuarantined;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStockDistribution {
        private String name;
        private Long value;
        private String color;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryHealthBar {
        private String category;
        private Long lowStock;
        private Long overStock;
        private Long outOfStock;
        private Long optimal;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UrgentAlert {
        private Long id;
        private String type; // "EXPIRY", "QUARANTINE", "LOW_STOCK"
        private String title;
        private String description;
        private String severity; // "critical", "warning"
        private String time;
    }
}
