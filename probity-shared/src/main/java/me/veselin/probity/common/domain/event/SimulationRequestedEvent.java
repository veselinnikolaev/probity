package me.veselin.probity.common.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event published when a simulation is requested, before execution begins.
 */
public record SimulationRequestedEvent(
    UUID simulationId,
    UUID portfolioId,
    UUID userId,
    int numberOfSimulations,
    int timeHorizonDays,
    double confidenceLevel,
    Double assumedReturnPercent,
    Double assumedVolatilityPercent,
    Map<String, BigDecimal> marketDataSnapshot,
    Instant occurredAt
) implements DomainEvent {}
