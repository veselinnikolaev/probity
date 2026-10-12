package me.veselin.probity.common.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a portfolio is created, updated, or has positions modified.
 */
public record PortfolioUpdatedEvent(
    UUID portfolioId,
    UUID userId,
    Instant occurredAt
) implements DomainEvent {}
