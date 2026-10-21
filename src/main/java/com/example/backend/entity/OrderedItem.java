package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ordered_items")
@Getter
@Setter
public class OrderedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orderId")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skuId")
    private Sku sku;

    @Column(nullable = false)
    private Long orderedUnits;

    @Column(name = "receivedUnits", columnDefinition = "bigint default 0")
    private Long receivedUnits = 0L;

    @Column(nullable = false)
    private Long price;

    @Column(name = "pricePerUnit")
    private Double pricePerUnit;

    @PrePersist
    @PreUpdate
    private void calculatePricePerUnit() {
        if (this.orderedUnits != null && this.orderedUnits > 0 && this.price != null) {
            this.pricePerUnit = Math.round(((double) this.price / this.orderedUnits) * 100.0) / 100.0;
        } else if (this.pricePerUnit == null) {
            this.pricePerUnit = 0.0;
        }
    }

}
