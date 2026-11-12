package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "reorder_quotation_items", indexes = {
    @Index(name = "idx_quotation_items_quotation", columnList = "quotationId"),
    @Index(name = "idx_quotation_items_sku", columnList = "skuId")
})
@Getter
@Setter
public class ReorderQuotationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotationId", nullable = false)
    private ReorderQuotation quotation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skuId", nullable = false)
    private Sku sku;

    @Column(nullable = false)
    private Long requestedUnits;

    @Column(nullable = false)
    private Long estimatedPrice = 0L;

    @Column(length = 500)
    private String notes;

    @PrePersist
    @PreUpdate
    private void sanitize() {
        if (this.estimatedPrice == null) {
            this.estimatedPrice = 0L;
        }
        if (this.requestedUnits == null || this.requestedUnits < 1) {
            this.requestedUnits = 1L;
        }
    }
}

