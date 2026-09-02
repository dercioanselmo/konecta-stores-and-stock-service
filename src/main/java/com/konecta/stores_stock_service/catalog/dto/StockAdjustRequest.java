package com.konecta.stores_stock_service.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockAdjustRequest(
        @NotNull(message = "é obrigatório")
        @Min(value = 0, message = "não pode ser negativo") Integer quantity) {
}
