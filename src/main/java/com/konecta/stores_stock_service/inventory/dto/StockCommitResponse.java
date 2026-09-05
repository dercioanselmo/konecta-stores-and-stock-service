package com.konecta.stores_stock_service.inventory.dto;

import java.util.List;
import java.util.UUID;

public record StockCommitResponse(UUID orderId, List<StockCommitLineResponse> items) {
}
