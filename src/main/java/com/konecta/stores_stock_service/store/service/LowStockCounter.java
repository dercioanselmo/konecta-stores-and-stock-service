package com.konecta.stores_stock_service.store.service;

import java.util.UUID;

/**
 * Thin seam so the store module can render lowStockCount on shop cards
 * without depending on inventory's internals directly.
 */
public interface LowStockCounter {

    long countLowStock(UUID storeId);
}
