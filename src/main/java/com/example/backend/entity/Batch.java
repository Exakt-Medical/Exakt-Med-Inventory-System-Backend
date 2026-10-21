package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "batches", indexes = {
    @Index(name = "idx_batch_facility_received", columnList = "facilityId, receivedAt"),
    @Index(name = "idx_batch_facility_num", columnList = "facilityId, batchNum"),
    @Index(name = "idx_batch_facility_status", columnList = "facilityId, status"),
    @Index(name = "idx_batch_location", columnList = "locationId")
})
@Getter
@Setter
public class Batch {

    public enum Status {
        Quarantined,
        Available,
        Expired
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facilityId", nullable = false)
    private Facility facility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orderedItemId")
    private OrderedItem orderedItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locationId")
    private LibLocation location;

    @Column(nullable = false)
    private String batchNum;

    @Column(nullable = false)
    private Long units = 0L;

    @Column(nullable = true)
    private LocalDate manufactureDate;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.Available;

    private String notes;

    @Column(name = "skuDeducted", columnDefinition = "boolean default false")
    private Boolean skuDeducted = false;

    public Boolean getSkuDeducted() {
        return skuDeducted != null ? skuDeducted : false;
    }

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    private void onReceive() {
        receivedAt = LocalDateTime.now();
    }

}
