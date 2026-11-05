package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "pharmacies")
@Getter
@Setter
public class Pharmacy {

    public enum Status {
        Active,
        Inactive
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pharmacy_code", nullable = false, unique = true)
    private String pharmacyCode;

    @Column(name = "pharmacy_name", nullable = false)
    private String pharmacyName;

    @ManyToOne
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Column(name = "lgu_id")
    private Long lguId;

    private String address;

    @Column(name = "license_number")
    private String licenseNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.Active;

    @Column(name = "created_At", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_At")
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        if (this.status == null) {
            this.status = Status.Active;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
