package com.konecta.stores_stock_service.inventory.service;

import java.util.List;
import java.util.UUID;

/**
 * Thrown when a stock commit can't be satisfied for one or more lines.
 * Carries enough detail ({@link FailedItem}) for the caller to build a
 * specific error message, unlike {@link com.konecta.stores_stock_service.common.ApiException}'s
 * flat string details — mirrors the extended-error-shape pattern the Cart
 * service already uses for its own {@code StoreMismatchError}.
 */
public class InsufficientStockException extends RuntimeException {

    private final List<FailedItem> failedItems;

    public InsufficientStockException(List<FailedItem> failedItems) {
        super("Stock insuficiente para um ou mais produtos.");
        this.failedItems = failedItems;
    }

    public List<FailedItem> getFailedItems() {
        return failedItems;
    }

    public record FailedItem(UUID productId, int requested, int available) {
    }
}
