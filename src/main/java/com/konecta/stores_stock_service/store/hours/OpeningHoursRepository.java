package com.konecta.stores_stock_service.store.hours;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpeningHoursRepository extends JpaRepository<OpeningHour, UUID> {

    List<OpeningHour> findByStoreId(UUID storeId);

    void deleteByStoreId(UUID storeId);
}
