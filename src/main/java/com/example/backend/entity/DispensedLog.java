package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispensed_logs")
@Getter
@Setter
public class DispensedLog {

    public enum Status {
        Pending,
        Received,
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skuId")
    private  Sku sku;

    @Column(nullable = false)
    private String patientName;

    @Column(nullable = false)
    private String contactNumber;

    @Column(nullable = false)
    private Long unitsDispensed;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dispensedAt;

    private LocalDateTime receivedAt;

    @PrePersist
    private void onCreate() {
        dispensedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void onUpdate() {
        if (status == Status.Received) {
            receivedAt = LocalDateTime.now();
        }
    }
}
