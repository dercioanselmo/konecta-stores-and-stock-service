package com.konecta.stores_stock_service.inventory.dto;

import java.util.UUID;

public record StockCommitLineResponse(UUID productId, int stockQuantity) {
}
