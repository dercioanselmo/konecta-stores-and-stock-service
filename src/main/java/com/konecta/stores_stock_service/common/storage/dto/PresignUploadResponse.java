package com.konecta.stores_stock_service.common.storage.dto;

import java.time.Instant;

public record PresignUploadResponse(String uploadUrl, String key, Instant expiresAt) {
}
