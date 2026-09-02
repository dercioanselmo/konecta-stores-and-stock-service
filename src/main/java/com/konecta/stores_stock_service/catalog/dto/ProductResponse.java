package com.konecta.stores_stock_service.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID shopId,
        String name,
        String description,
        UUID subcategoryId,
        String subcategoryName,
        UUID categoryId,
        String categoryName,
        BigDecimal price,
        int stockQuantity,
        int lowStockThreshold,
        boolean active,
        boolean lowStock,
        List<String> imageUrls,
        String primaryImageUrl,
        Instant createdAt,
        Instant updatedAt) {
}
