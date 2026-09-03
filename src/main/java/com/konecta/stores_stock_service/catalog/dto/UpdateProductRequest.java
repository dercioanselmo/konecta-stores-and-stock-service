package com.konecta.stores_stock_service.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.UUID;

public record UpdateProductRequest(
        String name,
        String description,
        UUID subcategoryId,
        @DecimalMin(value = "0", inclusive = true, message = "não pode ser negativo") BigDecimal price,
        Integer lowStockThreshold,
        Boolean active) {
}
