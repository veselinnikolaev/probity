package me.veselin.probity.portfolio.dto;

import java.util.List;

/**
 * Portfolio query DTO grouping compact time-series used in dashboard sparkline widgets.
 */
public record SparklineDto(
        List<Double> totalValue,
        List<Double> dailyReturn,
        List<Double> volatility,
        List<Double> sharpeRatio,
        List<Double> var95
) {}
