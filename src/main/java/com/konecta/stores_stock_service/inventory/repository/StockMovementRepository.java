package com.konecta.stores_stock_service.inventory.repository;

import com.konecta.stores_stock_service.inventory.model.StockMovement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    boolean existsByRefTypeAndRefId(String refType, String refId);
}
