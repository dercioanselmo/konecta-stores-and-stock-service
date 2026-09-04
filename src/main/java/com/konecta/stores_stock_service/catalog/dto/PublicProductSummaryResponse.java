package com.konecta.stores_stock_service.catalog.dto;

import java.util.UUID;

public record PublicProductSummaryResponse(UUID id, String name, String photoUrl) {
}
