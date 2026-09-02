package com.konecta.stores_stock_service.store.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record CreateShopRequest(
        @NotBlank(message = "não pode estar em branco") String name,
        String nuit,
        @NotBlank(message = "não pode estar em branco") String address,
        @NotBlank(message = "não pode estar em branco") String city,
        String neighborhood,
        String phone,
        List<UUID> categoryIds,
        String description) {
}
