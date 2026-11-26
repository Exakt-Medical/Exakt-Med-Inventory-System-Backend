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
    name = "generic_stock_settings",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_generic_stock_setting",
            columnNames = {"pharmacy_id", "medicine_id", "packaging_unit"}
        )
    },
    indexes = {
        @Index(name = "idx_gss_pharmacy", columnList = "pharmacy_id"),
        @Index(name = "idx_gss_pharmacy_medicine", columnList = "pharmacy_id, medicine_id"),
        @Index(name = "idx_gss_pharmacy_total_units", columnList = "pharmacy_id, total_units")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GenericStockSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medicine_id", nullable = false)
    private LibMedicine libMedicine;

    @Column(name = "dosage_form", nullable = false)
    private String dosageForm;

    @Column(name = "dosage_strength")
    private String dosageStrength;

    @Column(name = "packaging_unit", nullable = false)
    private String packagingUnit;

    @Column(name = "minimum_level", nullable = false)
    private Long minimumLevel;

    @Column(name = "reorder_level", nullable = false)
    private Long reorderLevel;

    @Column(name = "maximum_level", nullable = false)
    private Long maximumLevel;

    @Column(name = "total_units", nullable = false)
    private Long totalUnits = 0L;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        if (this.totalUnits == null) {
            this.totalUnits = 0L;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Manila"));
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Manila"));
    }
}

