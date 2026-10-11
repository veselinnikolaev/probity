package me.veselin.probity.simulation.domain;

import java.util.List;

/**
 * Value object stored as JSONB in the simulations table.
 * Mirrors the frontend SimulationResult shape exactly so serialisation
 * to the API response is a straight pass-through from the DB column.
 */
public record SimulationPayload(
        Statistics statistics,
        Outcomes outcomes,
        List<PercentileSeries> percentileSeries,
        List<SimulatedPath> allPaths,
        List<DistributionBucket> distribution
) {

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

    public record PercentileSeries(
            int percentile,
            double finalValue,
            List<PortfolioPath> values
    ) {}

    public record SimulatedPath(
            int pathId,
            List<PortfolioPath> values
    ) {}

    public record PortfolioPath(
            int dayIndex,
            double value
    ) {}

    public record DistributionBucket(
            double rangeMin,
            double rangeMax,
            int count,
            double percentage
    ) {}
}
