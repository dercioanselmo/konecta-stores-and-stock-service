package com.konecta.stores_stock_service.store.dto;

import java.util.UUID;

public record PublicShopResponse(
        UUID id,
        String name,
        String logoUrl,
        String coverUrl,
        boolean isOpen,
        double distanceKm) {
}
