package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "lib_locations", indexes = {
    @Index(name = "idx_lib_loc_facility", columnList = "facility_id"),
    @Index(name = "idx_lib_loc_code", columnList = "code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LibLocation {

    public enum StorageType {
        ROOM_TEMPERATURE,   // Standard ambient storage (15°C - 25°C)
        COLD_STORAGE,       // Refrigerated (2°C - 8°C)
        FREEZER,            // Frozen (-20°C or below)
        HAZARDOUS,          // Cytotoxic / Flammables / Hazardous chemicals
        OTHER               // General / Quarantine / Staging
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id")
    private Facility facility;

    @Column(nullable = false, length = 50)
    private String code; // e.g., "SHELF-A1", "REF-01"

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", length = 30, nullable = false)
    @Builder.Default
    private StorageType storageType = StorageType.ROOM_TEMPERATURE;

    @Column(length = 255)
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.isActive == null) {
            this.isActive = true;
        }
        if (this.storageType == null) {
            this.storageType = StorageType.ROOM_TEMPERATURE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
