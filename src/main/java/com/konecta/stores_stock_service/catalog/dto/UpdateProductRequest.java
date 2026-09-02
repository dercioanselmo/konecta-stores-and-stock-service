package com.konecta.stores_stock_service.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.List;

public record UpdateProductRequest(
        String name,
        String description,
        String category,
        @DecimalMin(value = "0", inclusive = true) BigDecimal price,
        Integer lowStockThreshold,
        Boolean active,
        List<String> imageUrls,
        String primaryImageUrl) {
}
