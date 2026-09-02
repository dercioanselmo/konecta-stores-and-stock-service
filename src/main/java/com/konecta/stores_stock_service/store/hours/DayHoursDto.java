package com.konecta.stores_stock_service.store.hours;

import java.time.LocalTime;

public record DayHoursDto(WeekDay day, LocalTime opensAt, LocalTime closesAt, boolean closed) {
}
