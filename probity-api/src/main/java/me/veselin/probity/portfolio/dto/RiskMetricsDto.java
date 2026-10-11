package me.veselin.probity.portfolio.dto;

import java.util.List;

/**
 * Query-layer DTO for aggregated portfolio risk factors and trend sparklines.
 */
public record RiskMetricsDto(
        double beta,
        double betaDelta,
        double maxDrawdown,
        double maxDrawdownDelta,
        double concentrationIndex,
        double concentrationDelta,
        List<Double> maxDrawdownSparkline,
        List<Double> betaSparkline,
        List<Double> concentrationSparkline
) {
    public static RiskMetricsDto empty() {
        List<Double> empty = List.of();
        return new RiskMetricsDto(0, 0, 0, 0, 0, 0, empty, empty, empty);
    }
}
