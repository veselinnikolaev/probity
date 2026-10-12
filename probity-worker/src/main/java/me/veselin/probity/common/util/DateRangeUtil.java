package me.veselin.probity.common.util;

import me.veselin.probity.portfolio.enumeration.DateRange;

import java.time.LocalDate;

/**
 * Unified utility for date range calculations across portfolio and simulation services.
 * Centralizes repeated date calculation logic to eliminate duplication.
 */
public final class DateRangeUtil {

    private DateRangeUtil() {}

    /**
     * Calculates a date range ending at the last completed trading day.
     * Used for historical data fetching across portfolio risk and simulation services.
     *
     * @param lookbackDays number of days to look back from the last trading day
     * @return DateRangeResult with from and to dates
     */
    public static DateRangeResult calculateHistoricalRange(int lookbackDays) {
        LocalDate to = TradingUtil.lastCompletedTradingDay();
        LocalDate from = to.minusDays(lookbackDays);
        return new DateRangeResult(from, to);
    }

    /**
     * Calculates a date range for a given date range enum, ending at the last completed trading day.
     *
     * @param range the date range enum value
     * @return DateRangeResult with from and to dates
     */
    public static DateRangeResult calculateRangeFromDateRangeEnum(DateRange range) {
        LocalDate to = TradingUtil.lastCompletedTradingDay();
        LocalDate from = range.toStartDate(to);
        return new DateRangeResult(from, to);
    }

    /**
     * Simple value object representing a date range.
     */
    public record DateRangeResult(LocalDate from, LocalDate to) {
    }
}
