package com.konecta.stores_stock_service.store.dto;

import com.konecta.stores_stock_service.store.model.StoreStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminShopSummaryResponse(
        UUID id,
        String name,
        String logoUrl,
        StoreStatus status,
        boolean isOpen,
        String ownerId,
        String ownerName,
        String ownerEmail,
        Instant createdAt) {
}
