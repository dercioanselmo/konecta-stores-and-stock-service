package com.konecta.stores_stock_service.store.hours;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateHoursRequest(@NotEmpty @Valid List<DayHoursDto> days) {
}
