package com.konecta.stores_stock_service.store.repository;

import com.konecta.stores_stock_service.store.model.StoreCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreCategoryRepository extends JpaRepository<StoreCategory, UUID> {

    List<StoreCategory> findByStoreId(UUID storeId);

    void deleteByStoreId(UUID storeId);

    boolean existsByCategoryId(UUID categoryId);
}
