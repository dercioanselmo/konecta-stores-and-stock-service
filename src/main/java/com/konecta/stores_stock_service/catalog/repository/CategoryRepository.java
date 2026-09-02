package com.konecta.stores_stock_service.catalog.repository;

import com.konecta.stores_stock_service.catalog.model.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByActiveTrueOrderBySortOrderAsc();

    List<Category> findAllByOrderBySortOrderAsc();

    Optional<Category> findByCode(String code);

    boolean existsByCode(String code);
}
