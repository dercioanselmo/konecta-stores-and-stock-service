package com.konecta.stores_stock_service.common.storage.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmUploadRequest(@NotBlank(message = "é obrigatório") String key) {
}
