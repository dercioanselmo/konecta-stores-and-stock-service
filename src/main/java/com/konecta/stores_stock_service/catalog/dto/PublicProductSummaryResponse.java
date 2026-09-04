package com.konecta.stores_stock_service.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicProductSummaryResponse(UUID id, String name, String photoUrl, BigDecimal price, boolean inStock) {
}
