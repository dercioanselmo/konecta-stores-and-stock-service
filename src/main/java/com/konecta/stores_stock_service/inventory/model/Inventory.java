package com.konecta.stores_stock_service.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
public class Inventory {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;

    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved = 0;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 5;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Inventory(UUID productId, int quantityAvailable, int lowStockThreshold) {
        this.productId = productId;
        this.quantityAvailable = quantityAvailable;
        this.lowStockThreshold = lowStockThreshold;
    }

    public boolean isLowStock() {
        return quantityAvailable <= lowStockThreshold;
    }
}
