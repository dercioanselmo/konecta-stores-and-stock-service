package com.konecta.stores_stock_service.store.dto;

import com.konecta.stores_stock_service.catalog.dto.CategoryResponse;
import java.util.List;
import java.util.UUID;

public record PublicShopDetailResponse(
        UUID id,
        String name,
        String logoUrl,
        String coverUrl,
        boolean isOpen,
        List<CategoryResponse> categories) {
}
