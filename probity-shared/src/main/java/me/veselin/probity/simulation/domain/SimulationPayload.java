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

    /**
     * The same payload with the path array emptied — what the Q9a storage split
     * (docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §10) stores inline in
     * {@code simulations.result_payload} once the paths live in {@code simulation_paths}.
     * The dual-source read in the worker's SimulationMapper puts the two halves back
     * together, so a reader of either half alone sees exactly the pre-split shape.
     */
    public SimulationPayload withoutAllPaths() {
        return new SimulationPayload(statistics, outcomes, percentileSeries, List.of(), distribution);
    }
}
