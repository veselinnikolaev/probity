package me.veselin.probity.common.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

public final class TradingUtil {

    // US Eastern — NYSE closes at 16:00 ET, so "last completed day" is anchored here.
    public static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");

    private TradingUtil() {}

    /**
     * Returns the most recent trading day whose closing bar is definitively
     * available, anchored to US Eastern Time. Skips backwards over weekends.
     * Holidays are not enumerated — Yahoo simply returns no bars for them.
     */
    public static LocalDate lastCompletedTradingDay() {
        LocalDate candidate = LocalDate.now(MARKET_ZONE).minusDays(1);
        while (isWeekend(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    /**
     * Clamps {@code requested} to at most {@link #lastCompletedTradingDay()},
     * ensuring we never request data Yahoo Finance hasn't published yet.
     */
    public static LocalDate clampToLastCompletedTradingDay(LocalDate requested) {
        LocalDate ceiling = lastCompletedTradingDay();
        return requested.isBefore(ceiling) ? requested : ceiling;
    }

    public static LocalDate nextTradingDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (isWeekend(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    public static LocalDate prevTradingDay(LocalDate date) {
        LocalDate prev = date.minusDays(1);
        while (isWeekend(prev)) {
            prev = prev.minusDays(1);
        }
        return prev;
    }

    public static boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    /** @deprecated Use {@link #lastCompletedTradingDay()} */
    @Deprecated
    public static LocalDate lastTradingDay() {
        return lastCompletedTradingDay();
    }
}