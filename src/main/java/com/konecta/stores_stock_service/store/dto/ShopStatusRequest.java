package com.konecta.stores_stock_service.store.dto;

import jakarta.validation.constraints.NotNull;

public record ShopStatusRequest(@NotNull Boolean manuallyClosed, String reason) {
}
