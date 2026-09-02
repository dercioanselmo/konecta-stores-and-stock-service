package com.konecta.stores_stock_service.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record CreateProductRequest(
        @NotBlank String name,
        @NotBlank String description,
        String category,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal price,
        @NotNull @Min(0) Integer stockQuantity,
        Integer lowStockThreshold,
        Boolean active,
        List<String> imageUrls,
        String primaryImageUrl) {
}
