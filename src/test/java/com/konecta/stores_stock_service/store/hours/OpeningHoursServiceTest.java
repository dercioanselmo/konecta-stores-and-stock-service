package com.konecta.stores_stock_service.store.hours;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.konecta.stores_stock_service.store.hours.model.OpeningHour;
import com.konecta.stores_stock_service.store.hours.model.WeekDay;
import com.konecta.stores_stock_service.store.hours.repository.OpeningHoursRepository;
import com.konecta.stores_stock_service.store.hours.service.OpeningHoursService;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpeningHoursServiceTest {

    @Mock
    private OpeningHoursRepository repository;

    private final UUID storeId = UUID.randomUUID();

    private WeekDay today() {
        return WeekDay.fromJavaDayOfWeek(ZonedDateTime.now(OpeningHoursService.MAPUTO).getDayOfWeek());
    }

    @Test
    void isOpenNow_true_whenWithinTodaysWindow() {
        OpeningHour hour = new OpeningHour(storeId, today(), LocalTime.of(0, 0), LocalTime.of(23, 59), false);
        when(repository.findByStoreId(storeId)).thenReturn(List.of(hour));

        OpeningHoursService service = new OpeningHoursService(repository);
        assertThat(service.isOpenNow(storeId)).isTrue();
    }

    @Test
    void isOpenNow_false_whenMarkedClosedToday() {
        OpeningHour hour = new OpeningHour(storeId, today(), null, null, true);
        when(repository.findByStoreId(storeId)).thenReturn(List.of(hour));

        OpeningHoursService service = new OpeningHoursService(repository);
        assertThat(service.isOpenNow(storeId)).isFalse();
    }

    @Test
    void isOpenNow_false_whenNoHoursConfigured() {
        when(repository.findByStoreId(storeId)).thenReturn(List.of());

        OpeningHoursService service = new OpeningHoursService(repository);
        assertThat(service.isOpenNow(storeId)).isFalse();
    }

    @Test
    void isOpenNow_false_whenTodayHasNoConfiguredRow() {
        // hours configured for a *different* day than today -> no row for today's key
        WeekDay today = today();
        WeekDay otherDay = WeekDay.values()[(today.ordinal() + 1) % WeekDay.values().length];
        OpeningHour hour = new OpeningHour(storeId, otherDay, LocalTime.of(0, 0), LocalTime.of(23, 59), false);
        when(repository.findByStoreId(storeId)).thenReturn(List.of(hour));

        OpeningHoursService service = new OpeningHoursService(repository);
        assertThat(service.isOpenNow(storeId)).isFalse();
    }

    @Test
    void isOpenNow_handlesOvernightWindow() {
        // 20:00 -> 02:00: "open" spans midnight. A zero-width same time is always
        // inside this window definition (closesAt <= opensAt branch), so it must be true.
        OpeningHour hour = new OpeningHour(storeId, today(), LocalTime.of(20, 0), LocalTime.of(20, 0), false);
        when(repository.findByStoreId(storeId)).thenReturn(List.of(hour));

        OpeningHoursService service = new OpeningHoursService(repository);
        assertThat(service.isOpenNow(storeId)).isTrue();
    }
}
