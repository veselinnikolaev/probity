package me.veselin.probity.portfolio.dto;

/**
 * Portfolio query DTO for one timestamped point in a rolling-volatility series.
 */
public record VolatilityPointDto(
        String date,
        double value
) {}
