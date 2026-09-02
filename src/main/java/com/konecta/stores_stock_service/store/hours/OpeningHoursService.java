package com.konecta.stores_stock_service.store.hours;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpeningHoursService {

    public static final ZoneId MAPUTO = ZoneId.of("Africa/Maputo");

    private final OpeningHoursRepository repository;

    public OpeningHoursService(OpeningHoursRepository repository) {
        this.repository = repository;
    }

    public HoursResponse getHours(UUID storeId) {
        List<OpeningHour> hours = repository.findByStoreId(storeId);
        List<DayHoursDto> days = hours.stream()
                .sorted(Comparator.comparing(OpeningHour::getDayOfWeek))
                .map(h -> new DayHoursDto(h.getDayOfWeek(), h.getOpensAt(), h.getClosesAt(), h.isClosed()))
                .toList();
        return new HoursResponse(days);
    }

    @Transactional
    public HoursResponse replaceHours(UUID storeId, UpdateHoursRequest request) {
        repository.deleteByStoreId(storeId);
        List<OpeningHour> toSave = request.days().stream()
                .map(d -> new OpeningHour(storeId, d.day(), d.closed() ? null : d.opensAt(),
                        d.closed() ? null : d.closesAt(), d.closed()))
                .toList();
        repository.saveAll(toSave);
        return getHours(storeId);
    }

    public boolean isOpenNow(UUID storeId) {
        List<OpeningHour> hours = repository.findByStoreId(storeId);
        if (hours.isEmpty()) {
            return false;
        }
        Map<DayOfWeek, OpeningHour> byDay = hours.stream()
                .collect(Collectors.toMap(OpeningHour::getDayOfWeek, h -> h));
        ZonedDateTime now = ZonedDateTime.now(MAPUTO);
        OpeningHour today = byDay.get(now.getDayOfWeek());
        if (today == null || today.isClosed() || today.getOpensAt() == null || today.getClosesAt() == null) {
            return false;
        }
        LocalTime nowTime = now.toLocalTime();
        if (today.getClosesAt().isAfter(today.getOpensAt())) {
            return !nowTime.isBefore(today.getOpensAt()) && nowTime.isBefore(today.getClosesAt());
        }
        // overnight window (closesAt <= opensAt, e.g. 20:00 -> 02:00)
        return !nowTime.isBefore(today.getOpensAt()) || nowTime.isBefore(today.getClosesAt());
    }
}
