package me.veselin.probity.bff.dto.simulation;

import me.veselin.probity.simulation.domain.SimulationPayload;

import java.math.BigDecimal;
import java.util.List;

/**
 * BFF response DTO for simulation HTTP responses.
 *
 * <p>Q9b API split (docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §10): the per-path array no
 * longer travels with the result. This body carries the summary, the percentile bands and
 * the distribution only (≈ 30 KB instead of ≈ 3.90 MB); the paths are served by
 * {@code GET /api/v1/simulations/{id}/paths} as a bounded sample. This applies to
 * {@code POST /run} (201), {@code GET /{id}} and every row of {@code GET /simulations}.
 */
public record SimulationResponse(
        String id,
        String portfolioId,
        String createdAt,
        Parameters parameters,
        BigDecimal currentPortfolioValue,
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
