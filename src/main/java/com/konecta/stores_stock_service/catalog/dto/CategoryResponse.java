package com.konecta.stores_stock_service.catalog.dto;

import java.util.UUID;

public record CategoryResponse(UUID id, String code, String name, int sortOrder, boolean active, String imageUrl) {
}
