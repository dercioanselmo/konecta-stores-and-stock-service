package com.konecta.stores_stock_service.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockAdjustRequest(@NotNull @Min(0) Integer quantity) {
}
