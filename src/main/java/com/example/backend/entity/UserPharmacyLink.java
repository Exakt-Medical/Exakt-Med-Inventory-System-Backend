package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "user_pharmacy_links",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"userId", "pharmacyId"})
    }
)
@Getter
@Setter
public class UserPharmacyLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacyId", nullable = false)
    private Pharmacy pharmacy;

    @Column(updatable = false)
    private LocalDateTime assignedAt;

    @PrePersist
    private void onAssign() {
        assignedAt = LocalDateTime.now();
    }

}

