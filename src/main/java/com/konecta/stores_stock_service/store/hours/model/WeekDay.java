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
        return switch (javaDayOfWeek) {
            case MONDAY -> SEGUNDA;
            case TUESDAY -> TERCA;
            case WEDNESDAY -> QUARTA;
            case THURSDAY -> QUINTA;
            case FRIDAY -> SEXTA;
            case SATURDAY -> SABADO;
            case SUNDAY -> DOMINGO;
        };
    }
}
