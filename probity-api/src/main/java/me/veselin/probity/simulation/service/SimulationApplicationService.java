package me.veselin.probity.simulation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.portfolio.domain.Portfolio;
import me.veselin.probity.portfolio.port.portfolio.PortfolioQueryPort;
import me.veselin.probity.simulation.domain.SimulationPayload;
import me.veselin.probity.simulation.domain.SimulationStatus;
import me.veselin.probity.simulation.dto.SimulationData;
import me.veselin.probity.simulation.dto.SimulationStatusResponse;
import me.veselin.probity.simulation.port.SimulationPort;
import me.veselin.probity.simulation.persistence.SimulationRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for simulation operations.
 * Provides a layer between the BFF controller and the simulation port,
 * aligning with the hexagonal architecture pattern used in other modules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationApplicationService implements SimulationPort {

    private final MonteCarloSimulationService monteCarloSimulationService;
    private final SimulationRepository simulationRepository;
    private final PortfolioQueryPort portfolioQueryPort;

    /**
     * Executes a new Monte Carlo simulation for a user-owned portfolio.
     *
     * @param portfolioId the portfolio ID
     * @param numberOfSimulations number of simulation paths
     * @param timeHorizonDays time horizon in days
     * @param confidenceLevel confidence level for VaR calculation
     * @param assumedReturnPercent optional assumed return override
     * @param assumedVolatilityPercent optional assumed volatility override
     * @param userId the authenticated user's ID
     * @return the simulation result with persisted payload
     */
    public SimulationData runSimulation(UUID portfolioId, int numberOfSimulations, int timeHorizonDays, double confidenceLevel, Double assumedReturnPercent, Double assumedVolatilityPercent, UUID userId) {
        log.info("Application service: running simulation portfolioId={} userId={}", portfolioId, userId);
        return monteCarloSimulationService.run(portfolioId, numberOfSimulations, timeHorizonDays, confidenceLevel, assumedReturnPercent, assumedVolatilityPercent, userId);
    }

    /**
     * Initiates an asynchronous simulation run.
     * Creates a PENDING simulation record and publishes a Kafka event for processing.
     * Returns 202 Accepted with status location.
     */
    public SimulationStatusResponse runSimulationAsync(UUID portfolioId, int numberOfSimulations, int timeHorizonDays, double confidenceLevel, Double assumedReturnPercent, Double assumedVolatilityPercent, UUID userId, String idempotencyKey) {
        log.info("Application service: initiating async simulation portfolioId={} userId={}", portfolioId, userId);

        // Create PENDING simulation record first
        var pendingSimulation = me.veselin.probity.simulation.domain.Simulation.builder()
                .portfolioId(portfolioId)
                .userId(userId)
                .numberOfSimulations(numberOfSimulations)
                .timeHorizonDays(timeHorizonDays)
                .confidenceLevel(confidenceLevel)
                .assumedReturnPct(assumedReturnPercent)
                .assumedVolatilityPct(assumedVolatilityPercent)
                .currentPortfolioValue(java.math.BigDecimal.ZERO) // Will be updated by worker
                .resultPayload(null) // Will be set by worker
                .status(SimulationStatus.PENDING)
                .build();

        var saved = simulationRepository.save(pendingSimulation);

        // The Kafka event will be published by the controller after this returns
        // We return the status response immediately with PENDING status
        return buildStatusResponse(saved, SimulationStatus.PENDING, null);
    }

    /**
     * Executes an asynchronous simulation run for a row the caller has already claimed.
     * <p>
     * Pure pass-through: the claim is owned by the Kafka consumer, which also supplies the
     * fence token the write-back must present.
     */
    public SimulationData executeAsync(UUID simulationId, UUID portfolioId, int numberOfSimulations, int timeHorizonDays, double confidenceLevel, Double assumedReturnPercent, Double assumedVolatilityPercent, UUID userId, Map<String, BigDecimal> marketDataSnapshot, Instant fenceToken) {
        log.info("Application service: executing async simulation id={} portfolioId={} fence={}", simulationId, portfolioId, fenceToken);
        return monteCarloSimulationService.runAsync(simulationId, portfolioId, numberOfSimulations, timeHorizonDays, confidenceLevel, assumedReturnPercent, assumedVolatilityPercent, userId, marketDataSnapshot, fenceToken);
    }

    /**
     * Retrieves a previously run simulation by its ID.
     *
     * @param simulationId the simulation ID
     * @param userId the authenticated user's ID
     * @return the simulation result
     */
    public SimulationData getSimulation(UUID simulationId, UUID userId) {
        log.debug("Application service: retrieving simulation id={} userId={}", simulationId, userId);
        return monteCarloSimulationService.get(simulationId, userId);
    }

    /**
     * Gets the current status of a simulation.
     * Returns status and result if COMPLETED.
     */
    public SimulationStatusResponse getSimulationStatus(UUID simulationId, UUID userId) {
        log.debug("Application service: getting simulation status id={} userId={}", simulationId, userId);
        var simulation = simulationRepository.findByIdAndUserIdWithStatus(simulationId, userId)
                .orElseThrow(() -> new me.veselin.probity.simulation.exception.SimulationNotFoundException(
                        "Simulation not found: " + simulationId));

        var result = simulation.getStatus() == SimulationStatus.COMPLETED
                ? monteCarloSimulationService.get(simulationId, userId) // Get full data if completed
                : null;

        return buildStatusResponse(simulation, simulation.getStatus(), result);
    }

    /**
     * Lists all simulations for a specific portfolio.
     *
     * @param portfolioId the portfolio ID
     * @param userId the authenticated user's ID
     * @return list of simulation results
     */
    public List<SimulationData> listSimulations(UUID portfolioId, UUID userId) {
        log.debug("Application service: listing simulations portfolioId={} userId={}", portfolioId, userId);
        return monteCarloSimulationService.listForPortfolio(portfolioId, userId);
    }

    /**
     * Gets a portfolio with positions for market data snapshot capture.
     */
    public Portfolio getSimulationPortfolio(UUID portfolioId, UUID userId) {
        return portfolioQueryPort.loadPortfolioWithPositions(portfolioId, userId);
    }

    private SimulationStatusResponse buildStatusResponse(me.veselin.probity.simulation.domain.Simulation simulation, SimulationStatus status, SimulationData data) {
        var response = SimulationStatusResponse.builder()
                .id(simulation.getId())
                .portfolioId(simulation.getPortfolioId())
                .status(status.name())
                .createdAt(simulation.getCreatedAt())
                .updatedAt(simulation.getUpdatedAt())
                .build();

        if (data != null && status == SimulationStatus.COMPLETED) {
            response.setResult(mapToResult(data));
        }
        return response;
    }

    private SimulationStatusResponse.SimulationResponseResult mapToResult(SimulationData data) {
        return SimulationStatusResponse.SimulationResponseResult.builder()
                .currentPortfolioValue(data.currentPortfolioValue())
                .parameters(SimulationStatusResponse.SimulationResponseParameters.builder()
                        .numberOfSimulations(data.parameters().numberOfSimulations())
                        .timeHorizonDays(data.parameters().timeHorizonDays())
                        .confidenceLevel(data.parameters().confidenceLevel())
                        .build())
                .statistics(SimulationStatusResponse.SimulationResponseStatistics.builder()
                        .expectedFinalValue(data.statistics().expectedFinalValue())
                        .medianFinalValue(data.statistics().medianFinalValue())
                        .stdDeviation(data.statistics().stdDeviation())
                        .minValue(data.statistics().minValue())
                        .maxValue(data.statistics().maxValue())
                        .build())
                .outcomes(SimulationStatusResponse.SimulationResponseOutcomes.builder()
                        .probabilityOf10PercentLoss(data.outcomes().probabilityOf10PercentLoss())
                        .probabilityOf20PercentLoss(data.outcomes().probabilityOf20PercentLoss())
                        .valueAtRisk95(data.outcomes().valueAtRisk95())
                        .conditionalValueAtRisk95(data.outcomes().conditionalValueAtRisk95())
                        .build())
                .allPaths(data.allPaths().stream().map(this::mapPath).toList())
                .percentileSeries(data.percentileSeries().stream().map(this::mapPercentile).toList())
                .distribution(data.distribution().stream().map(this::mapBucket).toList())
                .build();
    }

    private SimulationStatusResponse.SimulationResponseAllPath mapPath(SimulationPayload.SimulatedPath path) {
        return SimulationStatusResponse.SimulationResponseAllPath.builder()
                .pathId(path.pathId())
                .values(path.values().stream().map(v -> SimulationStatusResponse.SimulationResponsePathPoint.builder()
                        .dayIndex(v.dayIndex())
                        .value(v.value())
                        .build()).toList())
                .build();
    }

    private SimulationStatusResponse.SimulationResponsePercentileSeries mapPercentile(SimulationPayload.PercentileSeries ps) {
        return SimulationStatusResponse.SimulationResponsePercentileSeries.builder()
                .percentile(ps.percentile())
                .targetFinalValue(ps.finalValue())
                .values(ps.values().stream().map(v -> SimulationStatusResponse.SimulationResponsePathPoint.builder()
                        .dayIndex(v.dayIndex())
                        .value(v.value())
                        .build()).toList())
                .build();
    }

    private SimulationStatusResponse.SimulationResponseDistributionBucket mapBucket(SimulationPayload.DistributionBucket bucket) {
        return SimulationStatusResponse.SimulationResponseDistributionBucket.builder()
                .lowerBound(bucket.rangeMin())
                .upperBound(bucket.rangeMax())
                .count(bucket.count())
                .percentage(bucket.percentage())
                .build();
    }
}
