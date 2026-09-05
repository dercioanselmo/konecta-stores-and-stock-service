package com.konecta.stores_stock_service.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StockCommitItemRequest(
        @NotNull(message = "é obrigatório") UUID productId,
        @NotNull(message = "é obrigatório")
        @Min(value = 1, message = "deve ser pelo menos 1") Integer quantity) {
}
