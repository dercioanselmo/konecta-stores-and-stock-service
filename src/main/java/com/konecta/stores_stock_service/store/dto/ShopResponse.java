package com.konecta.stores_stock_service.store.dto;

import com.konecta.stores_stock_service.catalog.dto.CategoryResponse;
import com.konecta.stores_stock_service.store.model.StoreStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShopResponse(
        UUID id,
        String name,
        String legalName,
        String nuit,
        String email,
        String phone,
        String address,
        String city,
        String neighborhood,
        List<CategoryResponse> categories,
        String description,
        String logoUrl,
        String coverUrl,
        StoreStatus status,
        boolean isOpen,
        boolean manuallyClosed,
        boolean activationReady,
        boolean acceptsPickup,
        boolean acceptsDelivery,
        Instant createdAt,
        Instant updatedAt) {
}
