package com.konecta.stores_stock_service.store.repository;

import com.konecta.stores_stock_service.store.model.Store;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StoreRepository extends JpaRepository<Store, UUID>, JpaSpecificationExecutor<Store> {

    List<Store> findByOwnerUserId(String ownerUserId);

    Optional<Store> findByIdAndOwnerUserId(UUID id, String ownerUserId);
}
