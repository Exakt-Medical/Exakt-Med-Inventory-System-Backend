package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reorder_quotations", indexes = {
    @Index(name = "idx_quotations_pharmacy_created", columnList = "pharmacyId, createdAt"),
    @Index(name = "idx_quotations_pharmacy_status", columnList = "pharmacyId, status"),
    @Index(name = "idx_quotations_num", columnList = "quotationNumber")
})
@Getter
@Setter
public class ReorderQuotation {

    public enum Status {
        Pending,
        Approved,
        Rejected,
        Cancelled
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String quotationNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacyId", nullable = false)
    private Pharmacy pharmacy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplierId", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orderId")
    private Order order;

    @Column(nullable = false)
    private String priority = "Normal";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.Pending;

    @Column(nullable = false)
    private Long totalUnits = 0L;

    @Column(nullable = false)
    private Long estimatedTotalCost = 0L;

    @Column(length = 1000)
    private String notes;

    @Column(length = 1000)
    private String rejectionReason;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReorderQuotationItem> items = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        if (this.status == null) {
            this.status = Status.Pending;
        }
        if (this.priority == null || this.priority.isBlank()) {
            this.priority = "Normal";
        }
        if (this.totalUnits == null) {
            this.totalUnits = 0L;
        }
        if (this.estimatedTotalCost == null) {
            this.estimatedTotalCost = 0L;
        }
        createdAt = LocalDateTime.now(ZoneId.of("Asia/Manila"));
        updatedAt = LocalDateTime.now(ZoneId.of("Asia/Manila"));
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.of("Asia/Manila"));
    }
}

