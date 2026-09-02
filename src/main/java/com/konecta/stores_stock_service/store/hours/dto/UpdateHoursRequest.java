package com.konecta.stores_stock_service.store.hours.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateHoursRequest(
        @NotEmpty(message = "não pode estar vazio") @Valid List<DayHoursDto> days) {
}
