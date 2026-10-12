package me.veselin.probity.portfolio.dto;

import java.util.List;

/**
 * Query-layer DTO for Value-at-Risk reporting and explanatory breakdowns.
 */
public record VaRReportDto(
        double valueAtRisk,
        double confidenceLevel,
        int timeHorizonDays,
        MethodologyDto methodology,
        List<Double> historicalSparkline,
        List<AssetVaRDto> assetBreakdown,
        List<ReturnBucketDto> returnDistribution
) {
    public record MethodologyDto(
            String formula,
            double portfolioVolatility,
            double zscore,
            double expectedReturn
    ) {}

    public record AssetVaRDto(
            String ticker,
            double weight,
            double individualVar,
            double contribution,
            double percentOfTotal
    ) {}

    public record ReturnBucketDto(
            String range,
            int frequency,
            boolean isLoss
    ) {}

    public static VaRReportDto empty(double confidenceLevel, int timeHorizonDays) {
        return new VaRReportDto(
                0, confidenceLevel, timeHorizonDays,
                new VaRReportDto.MethodologyDto("VaR = μ + Z × σ", 0, 0, 0),
                List.of(), List.of(), List.of()
        );
    }
}
