package com.konecta.stores_stock_service.catalog.model;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
public class ProductImage {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "is_primary")
    private boolean primary;

    @Column(name = "sort_order")
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public ProductImage(UUID productId, String objectKey, boolean primary, int sortOrder) {
        this.productId = productId;
        this.objectKey = objectKey;
        this.primary = primary;
        this.sortOrder = sortOrder;
    }
}
