package com.konecta.stores_stock_service.common;

import com.konecta.stores_stock_service.inventory.service.InsufficientStockException.FailedItem;
import java.time.Instant;
import java.util.List;

public record InsufficientStockError(String code, String message, List<FailedItem> failedItems, Instant timestamp) {

    public static InsufficientStockError of(String message, List<FailedItem> failedItems) {
        return new InsufficientStockError("INSUFFICIENT_STOCK", message, failedItems, Instant.now());
    }
}
