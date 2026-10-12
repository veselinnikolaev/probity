package me.veselin.probity.common.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a simulation fails during execution.
 */
public record SimulationFailedEvent(
    UUID simulationId,
    UUID portfolioId,
    UUID userId,
    String errorMessage,
    Instant occurredAt
) implements DomainEvent {}
