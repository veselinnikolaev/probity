package me.veselin.probity.simulation.port;

import me.veselin.probity.portfolio.domain.Portfolio;
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
     * Gets a portfolio with positions for market data snapshot capture.
     */
    Portfolio getSimulationPortfolio(UUID portfolioId, UUID userId);

    List<SimulationData> listSimulations(UUID portfolioId, UUID userId);
}