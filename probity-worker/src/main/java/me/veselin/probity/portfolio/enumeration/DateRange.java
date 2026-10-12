package me.veselin.probity.portfolio.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Getter
@RequiredArgsConstructor
public enum DateRange {
    SEVEN_DAYS("7d"),
    THIRTY_DAYS("30d"),
    NINETY_DAYS("90d"),
    ONE_YEAR("1y");

    private final String value;

    public LocalDate toStartDate(LocalDate to) {
        return switch (this) {
            case SEVEN_DAYS   -> to.minusDays(7);
            case THIRTY_DAYS  -> to.minusDays(30);
            case ONE_YEAR     -> to.minusYears(1);
            case NINETY_DAYS  -> to.minusDays(90);
        };
    }

    public static DateRange fromValue(String value) {
        for (DateRange r : values()) {
            if (r.value.equalsIgnoreCase(value)) return r;
        }
        return NINETY_DAYS; // sensible default
    }
}
