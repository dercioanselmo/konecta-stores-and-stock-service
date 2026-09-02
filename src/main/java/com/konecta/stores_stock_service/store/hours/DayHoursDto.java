package com.konecta.stores_stock_service.store.hours;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record DayHoursDto(DayOfWeek day, LocalTime opensAt, LocalTime closesAt, boolean closed) {
}
