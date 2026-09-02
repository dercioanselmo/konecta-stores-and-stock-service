package com.konecta.stores_stock_service.inventory.repository;

import com.konecta.stores_stock_service.inventory.model.Inventory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductId(UUID productId);
}
