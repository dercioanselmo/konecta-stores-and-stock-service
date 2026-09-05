package com.konecta.stores_stock_service.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicProductDetailResponse(
        UUID id,
        UUID shopId,
        String name,
        String description,
        String photoUrl,
        BigDecimal price,
        boolean inStock,
        String categoryName,
        UUID subcategoryId,
        String subcategoryName) {
}
