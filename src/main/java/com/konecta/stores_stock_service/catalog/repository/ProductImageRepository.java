package com.konecta.stores_stock_service.catalog.repository;

import com.konecta.stores_stock_service.catalog.model.ProductImage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderBySortOrderAsc(UUID productId);

    long countByProductId(UUID productId);
}
