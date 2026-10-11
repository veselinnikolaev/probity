package me.veselin.probity.portfolio.dto;

import java.util.List;

/**
 * Query-layer DTO for portfolio list/detail cards consumed by the BFF.
 */
public record PortfolioDto(
        String id,
        String name,
        double totalValue,
        String createdAt,
        int positionsCount,
        double volatility,
        double sharpeRatio,
        List<Double> sparklineData
) {}

