package com.konecta.stores_stock_service.store.hours.dto;

import com.konecta.stores_stock_service.store.hours.model.WeekDay;
import java.time.LocalTime;

public record DayHoursDto(WeekDay day, LocalTime opensAt, LocalTime closesAt, boolean closed) {
}
