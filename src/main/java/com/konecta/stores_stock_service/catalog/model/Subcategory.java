package com.konecta.stores_stock_service.catalog.model;

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
@Table(name = "subcategories")
@Getter
@Setter
@NoArgsConstructor
public class Subcategory {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order")
    private int sortOrder;

    private boolean active = true;
}
