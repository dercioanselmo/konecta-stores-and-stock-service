package com.konecta.stores_stock_service.store.dto;

public record DashboardSummaryResponse(boolean isOpen, long productCount, long activeProductCount, long lowStockCount) {
}
