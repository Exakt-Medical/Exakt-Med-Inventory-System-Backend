package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "patients")
@Getter
@Setter
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @Column(nullable = false)
    private String name;

    private String contactNumber;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        createdAt = LocalDateTime.now(ZoneId.of("Asia/Manila"));
        updatedAt = LocalDateTime.now(ZoneId.of("Asia/Manila"));
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.of("Asia/Manila"));
    }

}
