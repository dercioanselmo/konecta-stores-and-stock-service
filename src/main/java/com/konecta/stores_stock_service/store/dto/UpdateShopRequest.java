package com.konecta.stores_stock_service.store.dto;

public record UpdateShopRequest(
        String name,
        String nuit,
        String address,
        String city,
        String neighborhood,
        String phone,
        String category,
        String description,
        String logoUrl,
        String coverUrl,
        Boolean acceptsPickup,
        Boolean acceptsDelivery) {
}
