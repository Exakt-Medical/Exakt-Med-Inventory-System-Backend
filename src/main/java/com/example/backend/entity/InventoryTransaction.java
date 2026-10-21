package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_transactions", indexes = {
        @Index(name = "idx_inv_tx_facility_sku", columnList = "facilityId, skuId, createdAt"),
        @Index(name = "idx_inv_tx_batch", columnList = "batchId"),
        @Index(name = "idx_inv_tx_created_at", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransaction {

    public enum Type {
        RECEIPT("PO Batch Receipt"),
        QUARANTINE_RELEASE("Quarantine Release"),
        ADJUSTMENT_ADD("Stock Adjustment (+)"),
        ADJUSTMENT_SUB("Stock Adjustment (-)"),
        DISPENSE("Dispensed"),
        EXPIRY_DEDUCTION("Batch Expiry Deduction"),
        INITIAL_STOCK("Initial Stock");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facilityId", nullable = false)
    private Facility facility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skuId", nullable = false)
    private Sku sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batchId")
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "transactionType", nullable = false, length = 40)
    private Type transactionType;

    @Column(name = "referenceNumber", length = 100)
    private String referenceNumber;

    @Column(name = "batchNum", length = 100)
    private String batchNum;

    @Column(name = "expiryDate")
    private LocalDate expiryDate;

    @Column(name = "quantityIn")
    private Long quantityIn;

    @Column(name = "quantityOut")
    private Long quantityOut;

    @Column(name = "previousBalance", nullable = false)
    private Long previousBalance;

    @Column(name = "balanceAfter", nullable = false)
    private Long balanceAfter;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(length = 1000)
    private String notes;

    @Column(name = "authorizedBy", length = 150)
    private String authorizedBy;

    @Column(name = "createdAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
