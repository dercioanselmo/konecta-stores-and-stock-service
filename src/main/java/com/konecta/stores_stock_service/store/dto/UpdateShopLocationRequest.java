package com.konecta.stores_stock_service.store.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateShopLocationRequest(
        @NotNull(message = "não pode estar em branco") Double latitude,
        @NotNull(message = "não pode estar em branco") Double longitude) {
}
