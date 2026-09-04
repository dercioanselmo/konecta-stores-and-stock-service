package com.konecta.stores_stock_service.catalog.dto;

import java.util.UUID;

public record SubcategoryResponse(
        UUID id,
        UUID categoryId,
        String categoryCode,
        String categoryName,
        String code,
        String name,
        int sortOrder,
        boolean active,
        String imageUrl) {
}
