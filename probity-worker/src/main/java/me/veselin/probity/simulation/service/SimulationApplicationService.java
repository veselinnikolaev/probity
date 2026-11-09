package me.veselin.probity.simulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.common.domain.event.SimulationRequestedEvent;
import me.veselin.probity.portfolio.domain.Portfolio;
import me.veselin.probity.portfolio.port.portfolio.PortfolioQueryPort;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationPayload;
import me.veselin.probity.simulation.domain.SimulationStatus;
import me.veselin.probity.simulation.dto.SimulationData;
import me.veselin.probity.simulation.dto.SimulationStatusResponse;
import me.veselin.probity.simulation.persistence.SimulationRepository;
import me.veselin.probity.simulation.port.SimulationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for simulation operations. Provides a layer between the BFF controller and
 * the simulation port, aligning with the hexagonal architecture pattern used in other modules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationApplicationService implements SimulationPort {

  private final MonteCarloSimulationService monteCarloSimulationService;
  private final SimulationRepository simulationRepository;
  private final PortfolioQueryPort portfolioQueryPort;
  private final ObjectMapper objectMapper;

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
  public SimulationData runSimulation(
      UUID portfolioId,
      int numberOfSimulations,
      int timeHorizonDays,
      double confidenceLevel,
      Double assumedReturnPercent,
      Double assumedVolatilityPercent,
      UUID userId) {
    log.info(
        "Application service: running simulation portfolioId={} userId={}", portfolioId, userId);
    return monteCarloSimulationService.run(
        portfolioId,
        numberOfSimulations,
        timeHorizonDays,
        confidenceLevel,
        assumedReturnPercent,
        assumedVolatilityPercent,
        userId);
  }

  /**
   * Initiates an asynchronous simulation run. Creates a PENDING simulation record and durably
   * queues its Kafka event for processing. Returns 202 Accepted with status location.
   *
   * <p>All database operations (simulation insert + outbox insert) happen in the same PostgreSQL
   * transaction. If either write fails, the entire transaction rolls back, ensuring the simulation
   * row and its associated event are always consistent.
   *
   * @param portfolioId the portfolio ID
   * @param numberOfSimulations number of simulation paths
   * @param timeHorizonDays time horizon in days
   * @param confidenceLevel confidence level for VaR calculation
   * @param assumedReturnPercent optional assumed return override
   * @param assumedVolatilityPercent optional assumed volatility override
   * @param userId the authenticated user's ID
   * @param idempotencyKey unique key for idempotent requests
   * @param marketDataSnapshot market data snapshot at request time for stale-price safety
   * @return 202 Accepted with status location
   */
  @Transactional
  public SimulationStatusResponse runSimulationAsync(
      UUID portfolioId,
      int numberOfSimulations,
      int timeHorizonDays,
      double confidenceLevel,
      Double assumedReturnPercent,
      Double assumedVolatilityPercent,
      UUID userId,
      String idempotencyKey,
      Map<String, BigDecimal> marketDataSnapshot) {
    log.info(
        "Application service: initiating async simulation portfolioId={} userId={}",
        portfolioId,
        userId);

    // Create PENDING simulation record first, then insert outbox event in the same transaction.
    // This guarantees that the simulation row and the event are durably associated:
    // if the app crashes after the commit, the outbox row survives and can be
    // re-published by the publisher on restart.
    var pendingSimulation =
        Simulation.builder()
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

    // Insert outbox event in the same transaction as the simulation row.
    // The outbox row links to the simulation and will be picked up by the
    // OutboxPublisher and published to Kafka after the HTTP response is sent.
    // If marketDataSnapshot is null/empty, an empty map is used (worker falls back to live prices).
    Map<String, BigDecimal> snapshot =
        marketDataSnapshot != null
            ? marketDataSnapshot
            : new java.util.HashMap<String, BigDecimal>();

    String jsonPayload;
    try {
      jsonPayload =
          objectMapper.writeValueAsString(
              new SimulationRequestedEvent(
                  saved.getId(),
                  portfolioId,
                  userId,
                  numberOfSimulations,
                  timeHorizonDays,
                  confidenceLevel,
                  assumedReturnPercent,
                  assumedVolatilityPercent,
                  snapshot,
                  Instant.now()));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize simulation event", e);
    }

    simulationRepository.insertOutboxEvent(
        saved.getId(), saved.getId(), "SimulationRequestedEvent", jsonPayload);

    // The Kafka event will be published by the OutboxPublisher after this returns.
    // We return the status response immediately with PENDING status.
    return buildStatusResponse(saved, SimulationStatus.PENDING, null);
  }

  /**
   * Executes an asynchronous simulation run for a row the caller has already claimed.
   *
   * <p>Pure pass-through: the claim is owned by the Kafka consumer, which also supplies the fence
   * token the write-back must present.
   */
  public SimulationData executeAsync(
      UUID simulationId,
      UUID portfolioId,
      int numberOfSimulations,
      int timeHorizonDays,
      double confidenceLevel,
      Double assumedReturnPercent,
      Double assumedVolatilityPercent,
      UUID userId,
      Map<String, BigDecimal> marketDataSnapshot,
      Instant fenceToken) {
    log.info(
        "Application service: executing async simulation id={} portfolioId={} fence={}",
        simulationId,
        portfolioId,
        fenceToken);
    return monteCarloSimulationService.runAsync(
        simulationId,
        portfolioId,
        numberOfSimulations,
        timeHorizonDays,
        confidenceLevel,
        assumedReturnPercent,
        assumedVolatilityPercent,
        userId,
        marketDataSnapshot,
        fenceToken);
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

  /** Gets the current status of a simulation. Returns status and result if COMPLETED. */
  public SimulationStatusResponse getSimulationStatus(UUID simulationId, UUID userId) {
    log.debug(
        "Application service: getting simulation status id={} userId={}", simulationId, userId);
    var simulation =
        simulationRepository
            .findByIdAndUserIdWithStatus(simulationId, userId)
            .orElseThrow(
                () ->
                    new me.veselin.probity.simulation.exception.SimulationNotFoundException(
                        "Simulation not found: " + simulationId));

    var result =
        simulation.getStatus() == SimulationStatus.COMPLETED
            ? monteCarloSimulationService.get(simulationId, userId) // Get full data if completed
            : null;

    return buildStatusResponse(simulation, simulation.getStatus(), result);
  }

  /**
   * The sampled paths of a simulation for the Q9b paths endpoint
   * (docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §10), backed by the Q9a dual-source read. The
   * ownership guard is the same one {@link #getSimulation} uses.
   */
  public SampledPaths getSimulationPaths(UUID simulationId, UUID userId, int n) {
    log.debug(
        "Application service: getting simulation paths id={} userId={} n={}",
        simulationId,
        userId,
        n);
    var result = monteCarloSimulationService.getPaths(simulationId, userId, n);
    return new SampledPaths(result.totalPaths(), result.paths());
  }

  /**
   * Lists all simulations for a specific portfolio.
   *
   * @param portfolioId the portfolio ID
   * @param userId the authenticated user's ID
   * @return list of simulation results
   */
  public List<SimulationData> listSimulations(UUID portfolioId, UUID userId) {
    log.debug(
        "Application service: listing simulations portfolioId={} userId={}", portfolioId, userId);
    return monteCarloSimulationService.listForPortfolio(portfolioId, userId);
  }

  /** Gets a portfolio with positions for market data snapshot capture. */
  public Portfolio getSimulationPortfolio(UUID portfolioId, UUID userId) {
    return portfolioQueryPort.loadPortfolioWithPositions(portfolioId, userId);
  }

  private SimulationStatusResponse buildStatusResponse(
      me.veselin.probity.simulation.domain.Simulation simulation,
      SimulationStatus status,
      SimulationData data) {
    var response =
        SimulationStatusResponse.builder()
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
        .parameters(
            SimulationStatusResponse.SimulationResponseParameters.builder()
                .numberOfSimulations(data.parameters().numberOfSimulations())
                .timeHorizonDays(data.parameters().timeHorizonDays())
                .confidenceLevel(data.parameters().confidenceLevel())
                .build())
        .statistics(
            SimulationStatusResponse.SimulationResponseStatistics.builder()
                .expectedFinalValue(data.statistics().expectedFinalValue())
                .medianFinalValue(data.statistics().medianFinalValue())
                .stdDeviation(data.statistics().stdDeviation())
                .minValue(data.statistics().minValue())
                .maxValue(data.statistics().maxValue())
                .build())
        .outcomes(
            SimulationStatusResponse.SimulationResponseOutcomes.builder()
                .probabilityOf10PercentLoss(data.outcomes().probabilityOf10PercentLoss())
                .probabilityOf20PercentLoss(data.outcomes().probabilityOf20PercentLoss())
                .valueAtRisk95(data.outcomes().valueAtRisk95())
                .conditionalValueAtRisk95(data.outcomes().conditionalValueAtRisk95())
                .build())
        .percentileSeries(data.percentileSeries().stream().map(this::mapPercentile).toList())
        .distribution(data.distribution().stream().map(this::mapBucket).toList())
        .build();
  }

  private SimulationStatusResponse.SimulationResponsePercentileSeries mapPercentile(
      SimulationPayload.PercentileSeries ps) {
    return SimulationStatusResponse.SimulationResponsePercentileSeries.builder()
        .percentile(ps.percentile())
        .targetFinalValue(ps.finalValue())
        .values(
            ps.values().stream()
                .map(
                    v ->
                        SimulationStatusResponse.SimulationResponsePathPoint.builder()
                            .dayIndex(v.dayIndex())
                            .value(v.value())
                            .build())
                .toList())
        .build();
  }

  private SimulationStatusResponse.SimulationResponseDistributionBucket mapBucket(
      SimulationPayload.DistributionBucket bucket) {
    return SimulationStatusResponse.SimulationResponseDistributionBucket.builder()
        .lowerBound(bucket.rangeMin())
        .upperBound(bucket.rangeMax())
        .count(bucket.count())
        .percentage(bucket.percentage())
        .build();
  }
}
