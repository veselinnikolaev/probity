package me.veselin.probity.portfolio.dto;

import me.veselin.probity.common.util.SeriesUtil;
import me.veselin.probity.portfolio.domain.PortfolioPosition;

/**
 * Query-layer DTO describing one portfolio position with valuation and risk attributes.
 */
public record PositionDto(
        String id,
        String ticker,
        String name,
        String assetType,
        double price,
        double change,
        double changePercent,
        int riskScore,
        String riskLevel,
        String volume,
        String positionValue,
        String sector,
        double weight,
        double volatility,
        double volatilityContribution,
        double quantity,
        double avgBuyPrice,
        double currentPrice,
        double positionValueRaw,
        double dailyReturn,
        double riskContribution
) {
    public static PositionDto empty(PortfolioPosition pos, String ticker, double posValue) {
        return new PositionDto(
                pos.getId().toString(), ticker,
                pos.getAsset().getName(), pos.getAsset().getType().name(),
                0.0, 0.0, 0.0, 0, "UNKNOWN", "N/A",
                SeriesUtil.formatPositionValue(posValue),
                pos.getAsset().getSector().getLabel(),
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.0
        );
    }
}
