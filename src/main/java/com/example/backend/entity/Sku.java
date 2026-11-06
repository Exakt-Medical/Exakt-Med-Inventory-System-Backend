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
        @UniqueConstraint(name = "uk_pharmacy_sku_name", columnNames = {"pharmacyId", "name"})
    },
    indexes = {
        @Index(name = "idx_sku_pharmacy_brand", columnList = "pharmacyId, brandName"),
        @Index(name = "idx_sku_pharmacy_units", columnList = "pharmacyId, units"),
        @Index(name = "idx_sku_pharmacy_name", columnList = "pharmacyId, name")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Sku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacyId", nullable = false)
    private Pharmacy pharmacy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medicineId")
    private LibMedicine libMedicine;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String brandName;

    @Column(nullable = false)
    private String dosageForm;

    @Column(name = "dosageStrength")
    private String dosageStrength;

    @Column(nullable = false)
    private String packagingUnit;

    @Column(nullable = false)
    private Long units = 0L;

    @Column(nullable = false)
    private Long minimumLevel;

    @Column(nullable = false)
    private Long reorderLevel;

    @Column(nullable = false)
    private Long maximumLevel;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        if (this.units == null) {
            this.units = 0L;
        }
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Manila"));
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Manila"));
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Manila"));
    }
}
