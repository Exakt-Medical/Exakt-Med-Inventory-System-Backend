package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "sku",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_facility_sku_name", columnNames = {"facilityId", "name"})
    },
    indexes = {
        @Index(name = "idx_sku_facility_brand", columnList = "facilityId, brandName"),
        @Index(name = "idx_sku_facility_units", columnList = "facilityId, units"),
        @Index(name = "idx_sku_facility_name", columnList = "facilityId, name")
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
    @JoinColumn(name = "facilityId")
    private Facility facility;

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
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
