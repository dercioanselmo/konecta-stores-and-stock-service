package com.konecta.stores_stock_service.store.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "store_categories")
@Getter
@Setter
@NoArgsConstructor
public class StoreCategory {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    public StoreCategory(UUID storeId, UUID categoryId) {
        this.storeId = storeId;
        this.categoryId = categoryId;
    }
}
