package com.konecta.stores_stock_service.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record StockCommitRequest(
        @NotNull(message = "é obrigatório") UUID orderId,
        @NotEmpty(message = "não pode estar vazio") List<@Valid StockCommitItemRequest> items) {
}
