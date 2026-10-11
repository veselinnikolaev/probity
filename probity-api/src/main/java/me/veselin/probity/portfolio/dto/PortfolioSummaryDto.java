package me.veselin.probity.portfolio.dto;

import java.util.List;

/**
 * Query-layer DTO for high-level portfolio performance and risk summary metrics.
 */
public record PortfolioSummaryDto(
        double totalValue,
        double totalValueDelta,
        double dailyReturn,
        double dailyReturnDelta,
        double volatility,
        double volatilityDelta,
        double sharpeRatio,
        double sharpeRatioDelta,
        double var95,
        double var95Delta,
        SparklineDto sparklines
) {
    public static PortfolioSummaryDto empty() {
        SparklineDto empty = new SparklineDto(
                List.of(), List.of(), List.of(), List.of(), List.of());
        return new PortfolioSummaryDto(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, empty);
    }
}
