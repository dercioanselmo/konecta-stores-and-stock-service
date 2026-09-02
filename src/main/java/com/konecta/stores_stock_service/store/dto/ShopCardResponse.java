package com.konecta.stores_stock_service.store.dto;

import java.util.UUID;

public record ShopCardResponse(UUID id, String name, String logoUrl, boolean isOpen, long lowStockCount) {
}
