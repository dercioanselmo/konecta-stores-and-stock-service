package com.konecta.stores_stock_service.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSubcategoryRequest(
        @NotBlank(message = "não pode estar em branco") String code,
        @NotBlank(message = "não pode estar em branco") String name,
        Integer sortOrder,
        Boolean active) {
}
