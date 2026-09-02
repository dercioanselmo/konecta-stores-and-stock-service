package com.konecta.stores_stock_service.store.hours.model;

import java.time.DayOfWeek;

/**
 * Days of the week as exposed to clients — the platform is Portuguese-language
 * (Mozambique), so API values use Portuguese day names rather than
 * {@link DayOfWeek}'s English ones. Declared Monday-first so enum ordinal
 * order already matches calendar week order.
 */
public enum WeekDay {
    SEGUNDA(DayOfWeek.MONDAY),
    TERCA(DayOfWeek.TUESDAY),
    QUARTA(DayOfWeek.WEDNESDAY),
    QUINTA(DayOfWeek.THURSDAY),
    SEXTA(DayOfWeek.FRIDAY),
    SABADO(DayOfWeek.SATURDAY),
    DOMINGO(DayOfWeek.SUNDAY);

    private final DayOfWeek javaDayOfWeek;

    WeekDay(DayOfWeek javaDayOfWeek) {
        this.javaDayOfWeek = javaDayOfWeek;
    }

    public DayOfWeek toJavaDayOfWeek() {
        return javaDayOfWeek;
    }

    public static WeekDay fromJavaDayOfWeek(DayOfWeek javaDayOfWeek) {
        // Both enums are declared Monday-first in the same order, so ordinal
        // position alone is the mapping — no switch/lookup table needed.
        return values()[javaDayOfWeek.ordinal()];
    }
}
