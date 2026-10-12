package me.veselin.probity.portfolio.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum Sector {
    TECHNOLOGY("Technology", "#3B82F6"),
    FINANCIAL("Financial", "#60A5FA"),
    HEALTHCARE("Healthcare", "#0EA5E9"),
    CRYPTO("Crypto", "#06B6D4"),
    COMMODITIES("Commodities", "#14B8A6"),
    ENERGY("Energy", "#F59E0B"),
    CONSUMER("Consumer", "#8B5CF6"),
    UNKNOWN("Unknown", "#6B7280");

    private final String label;
    private final String color;

    public static Sector from(String value) {
        return Arrays.stream(values())
                .filter(s -> s.label.equalsIgnoreCase(value))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
