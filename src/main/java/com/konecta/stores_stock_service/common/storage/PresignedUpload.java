package com.konecta.stores_stock_service.common.storage;

import java.time.Instant;

public record PresignedUpload(String uploadUrl, String key, Instant expiresAt) {
}
