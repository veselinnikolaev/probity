package me.veselin.probity.simulation.port;

import me.veselin.probity.portfolio.domain.Portfolio;
import me.veselin.probity.simulation.domain.SimulationPayload;
import me.veselin.probity.simulation.dto.SimulationData;
import me.veselin.probity.simulation.dto.SimulationStatusResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SimulationPort {
    SimulationData runSimulation(UUID portfolioId, int numberOfSimulations, int timeHorizonDays, double confidenceLevel, Double assumedReturnPercent, Double assumedVolatilityPercent, UUID userId);

    /**
     * Initiates an asynchronous simulation run.
     * Creates a PENDING simulation record and publishes a Kafka event for processing.
     * Returns 202 Accepted with status location.
     */
    SimulationStatusResponse runSimulationAsync(UUID portfolioId, int numberOfSimulations, int timeHorizonDays, double confidenceLevel, Double assumedReturnPercent, Double assumedVolatilityPercent, UUID userId, String idempotencyKey);

    /**
     * Executes an asynchronous simulation run for a row the caller has already claimed.
     *
     * <p>Pure execution: it does not claim, release or decide anything. The caller (the
     * Kafka consumer) owns the claim and passes the fence token that its write-back must
     * present, so there is exactly one component responsible for "is this delivery mine to
     * run" — the one that also holds the offset and the group identity.
     *
     * @param fenceToken the token returned by the claim this delivery's row was taken with
     */
    SimulationData executeAsync(UUID simulationId, UUID portfolioId, int numberOfSimulations, int timeHorizonDays, double confidenceLevel, Double assumedReturnPercent, Double assumedVolatilityPercent, UUID userId, Map<String, BigDecimal> marketDataSnapshot, Instant fenceToken);

    SimulationData getSimulation(UUID simulationId, UUID userId);

    /**
     * Gets the current status of a simulation.
     * Returns status and result if COMPLETED.
     */
    SimulationStatusResponse getSimulationStatus(UUID simulationId, UUID userId);

    /**
     * The sampled paths of a completed simulation for
     * {@code GET /api/v1/simulations/{id}/paths} (Q9b API split,
     * docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §10).
     *
     * <p>Same ownership guard as {@link #getSimulation}: a row that is not the caller's is
     * reported exactly as a missing one. {@code n} was validated by the caller against the
     * endpoint bounds; this returns the deterministic sample of at most {@code n} paths
     * (global min/max always included) plus the stored total, so the body the client sees is
     * bounded by the request.
     *
     * @param n maximum paths to sample; the caller has already bounded it
     */
    SampledPaths getSimulationPaths(UUID simulationId, UUID userId, int n);

    /**
     * The sampled paths plus the stored total, so a caller can report how much of the stored
     * data the sample covers without a second read.
     *
     * @param totalPaths number of stored paths before sampling
     * @param paths      the deterministic sample, in stored order, at most {@code n} long
     */
    record SampledPaths(int totalPaths, List<SimulationPayload.SimulatedPath> paths) {
    }

    /**
     * Gets a portfolio with positions for market data snapshot capture.
     */
    Portfolio getSimulationPortfolio(UUID portfolioId, UUID userId);

    List<SimulationData> listSimulations(UUID portfolioId, UUID userId);
}