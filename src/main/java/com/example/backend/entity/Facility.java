package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "facilities")
@Getter
@Setter
public class Facility {

    public enum Status {
        Active,
        Inactive
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long ipkrasFacilityId;

    @ManyToOne
    @JoinColumn(name = "projectId", nullable = false)
    private Project project;

    @Column(nullable = false, unique = true)
    private String facilityCode;

    @Column(nullable = false)
    private String name;

    @Column
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(updatable = false)
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
