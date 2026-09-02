package com.konecta.stores_stock_service.store.dto;

import jakarta.validation.constraints.NotNull;

public record ShopStatusRequest(
        @NotNull(message = "é obrigatório") Boolean manuallyClosed,
        String reason) {
}
