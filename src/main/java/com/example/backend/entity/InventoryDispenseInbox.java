package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "inventory_dispense_inbox")
@Getter
@Setter
public class InventoryDispenseInbox {

    public enum Status {
        PROCESSED,
        SKIPPED_DUPLICATE,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(name = "rx_number", nullable = false, length = 100)
    private String rxNumber;

    @Column(name = "pharmacy_code", nullable = false, length = 100)
    private String pharmacyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private Status status = Status.PROCESSED;

    @Column(name = "total_units_deducted")
    private Integer totalUnitsDeducted = 0;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Manila"));
        if (createdAt == null) {
            createdAt = now;
        }
        if (processedAt == null) {
            processedAt = now;
        }
        if (status == null) {
            status = Status.PROCESSED;
        }
        if (totalUnitsDeducted == null) {
            totalUnitsDeducted = 0;
        }
    }
}
