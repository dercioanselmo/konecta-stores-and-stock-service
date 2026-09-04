package com.konecta.stores_stock_service.store.dto;

import com.konecta.stores_stock_service.catalog.dto.CategoryResponse;
import java.util.List;
import java.util.UUID;

public record ShopCardResponse(UUID id, String name, String logoUrl, boolean isOpen, long lowStockCount,
        List<CategoryResponse> categories) {
}
