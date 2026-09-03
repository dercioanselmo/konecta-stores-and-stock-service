package com.konecta.stores_stock_service.common.storage.dto;

import jakarta.validation.constraints.NotBlank;

public record PresignUploadRequest(@NotBlank(message = "é obrigatório") String contentType) {
}
