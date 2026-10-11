package me.veselin.probity.simulation.dto;

import me.veselin.probity.simulation.domain.SimulationPayload;

import java.math.BigDecimal;
import java.util.List;

/**
 * Domain DTO for simulation data transferred between simulation module and BFF.
 */
public record SimulationData(
        String id,
        String portfolioId,
        String createdAt,
        Parameters parameters,
        BigDecimal currentPortfolioValue,
        List<SimulationPayload.SimulatedPath> allPaths,
        List<SimulationPayload.PercentileSeries> percentileSeries,
        Statistics statistics,
        Outcomes outcomes,
        List<SimulationPayload.DistributionBucket> distribution
) {
    public record Parameters(
            int numberOfSimulations,
            int timeHorizonDays,
            double confidenceLevel
    ) {}

    public record Statistics(
            double expectedFinalValue,
            double medianFinalValue,
            double stdDeviation,
            double minValue,
            double maxValue
    ) {}

    public record Outcomes(
            double probabilityOf10PercentLoss,
            double probabilityOf20PercentLoss,
            double valueAtRisk95,
            double conditionalValueAtRisk95
    ) {}
}
