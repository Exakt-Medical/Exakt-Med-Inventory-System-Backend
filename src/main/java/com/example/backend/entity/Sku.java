package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(
    name = "sku",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_gss_sku_name", columnNames = {"generic_stock_settings_id", "name"})
    },
    indexes = {
        @Index(name = "idx_sku_generic_stock_settings", columnList = "generic_stock_settings_id"),
        @Index(name = "idx_sku_brand_name", columnList = "brand_name"),
        @Index(name = "idx_sku_name", columnList = "name"),
        @Index(name = "idx_sku_status", columnList = "status"),
        @Index(name = "idx_sku_units", columnList = "units")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Sku {

    public enum Status {
        Active,
        Inactive
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generic_stock_settings_id", nullable = false)
    private GenericStockSetting genericStockSetting;

    @Column(nullable = false)
    private String name;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(nullable = false)
    private Long units = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.Active;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        if (this.units == null) {
            this.units = 0L;
        }
        if (this.status == null) {
            this.status = Status.Active;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Manila"));
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Manila"));
    }

    // Convenience @Transient getters delegating to genericStockSetting for downstream services

    @Transient
    public Pharmacy getPharmacy() {
        return genericStockSetting != null ? genericStockSetting.getPharmacy() : null;
    }

    @Transient
    public LibMedicine getLibMedicine() {
        return genericStockSetting != null ? genericStockSetting.getLibMedicine() : null;
    }

    @Transient
    public String getDosageForm() {
        return genericStockSetting != null ? genericStockSetting.getDosageForm() : null;
    }

    @Transient
    public String getDosageStrength() {
        return genericStockSetting != null ? genericStockSetting.getDosageStrength() : null;
    }

    @Transient
    public String getPackagingUnit() {
        return genericStockSetting != null ? genericStockSetting.getPackagingUnit() : null;
    }

    @Transient
    public Long getMinimumLevel() {
        return genericStockSetting != null ? genericStockSetting.getMinimumLevel() : null;
    }

    @Transient
    public Long getReorderLevel() {
        return genericStockSetting != null ? genericStockSetting.getReorderLevel() : null;
    }

    @Transient
    public Long getMaximumLevel() {
        return genericStockSetting != null ? genericStockSetting.getMaximumLevel() : null;
    }

    @Transient
    public Long getGroupTotalUnits() {
        return genericStockSetting != null ? genericStockSetting.getTotalUnits() : this.units;
    }
}
