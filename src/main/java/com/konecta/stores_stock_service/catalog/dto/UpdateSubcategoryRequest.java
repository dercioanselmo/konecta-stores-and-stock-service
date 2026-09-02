package com.konecta.stores_stock_service.catalog.dto;

public record UpdateSubcategoryRequest(String name, Integer sortOrder, Boolean active) {
}
