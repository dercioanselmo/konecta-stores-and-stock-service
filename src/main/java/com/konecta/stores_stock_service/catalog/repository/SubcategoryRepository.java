package com.konecta.stores_stock_service.catalog.repository;

import com.konecta.stores_stock_service.catalog.model.Subcategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubcategoryRepository extends JpaRepository<Subcategory, UUID> {

    List<Subcategory> findByCategoryIdOrderBySortOrderAsc(UUID categoryId);

    List<Subcategory> findByCategoryIdAndActiveTrueOrderBySortOrderAsc(UUID categoryId);

    Optional<Subcategory> findByCategoryIdAndCode(UUID categoryId, String code);

    boolean existsByCategoryId(UUID categoryId);
}
