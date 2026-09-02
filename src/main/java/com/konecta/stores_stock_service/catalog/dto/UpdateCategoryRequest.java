package com.konecta.stores_stock_service.catalog.dto;

public record UpdateCategoryRequest(String name, Integer sortOrder, Boolean active) {
}
