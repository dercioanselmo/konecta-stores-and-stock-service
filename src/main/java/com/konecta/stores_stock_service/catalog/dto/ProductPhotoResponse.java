package com.konecta.stores_stock_service.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record ProductPhotoResponse(UUID id, String url, @JsonProperty("isPrimary") boolean primary) {
}
