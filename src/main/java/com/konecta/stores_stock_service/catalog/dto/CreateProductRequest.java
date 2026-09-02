package com.konecta.stores_stock_service.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateProductRequest(
        @NotBlank(message = "não pode estar em branco") String name,
        @NotBlank(message = "não pode estar em branco") String description,
        UUID subcategoryId,
        @NotNull(message = "é obrigatório")
        @DecimalMin(value = "0", inclusive = true, message = "não pode ser negativo") BigDecimal price,
        @NotNull(message = "é obrigatório")
        @Min(value = 0, message = "não pode ser negativo") Integer stockQuantity,
        Integer lowStockThreshold,
        Boolean active,
        List<String> imageUrls,
        String primaryImageUrl) {
}
