package me.veselin.probity.common.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a simulation completes successfully.
 */
public record SimulationCompletedEvent(
    UUID simulationId,
    UUID portfolioId,
    UUID userId,
    BigDecimal currentPortfolioValue,
    Instant occurredAt
) implements DomainEvent {}
