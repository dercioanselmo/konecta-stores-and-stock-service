package com.konecta.stores_stock_service.store.dto;

import java.util.List;
import java.util.UUID;

public record UpdateShopRequest(
        String name,
        String nuit,
        String address,
        String city,
        String neighborhood,
        String phone,
        List<UUID> categoryIds,
        String description,
        String logoUrl,
        String coverUrl,
        Boolean acceptsPickup,
        Boolean acceptsDelivery) {
}
