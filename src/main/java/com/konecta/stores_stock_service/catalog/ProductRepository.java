package com.konecta.stores_stock_service.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndStoreId(UUID id, UUID storeId);

    List<Product> findByStoreId(UUID storeId);

    long countByStoreId(UUID storeId);

    long countByStoreIdAndStatus(UUID storeId, ProductStatus status);

    boolean existsBySubcategoryId(UUID subcategoryId);
}
