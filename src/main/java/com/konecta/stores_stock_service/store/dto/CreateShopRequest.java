package com.konecta.stores_stock_service.store.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateShopRequest(
        @NotBlank String name,
        String nuit,
        @NotBlank String address,
        @NotBlank String city,
        String neighborhood,
        String phone,
        String category,
        String description) {
}
