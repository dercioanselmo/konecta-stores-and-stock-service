package com.konecta.stores_stock_service.store.hours;

import java.util.List;

public record HoursResponse(List<DayHoursDto> days) {
}
