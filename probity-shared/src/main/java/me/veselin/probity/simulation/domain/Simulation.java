package me.veselin.probity.simulation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.veselin.probity.common.audit.BaseEntitySoftDelete;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aggregate root for the simulation bounded context.
 *
 * Extends BaseEntitySoftDelete — inherits:
 *   id (UUID), createdAt, updatedAt, deleted, deletedAt, version
 *
 * The full GBM result (paths, percentile series, statistics, distribution)
 * lives in result_payload (JSONB) — single-row lookup, no join overhead.
 *
 * current_portfolio_value uses NUMERIC(19,4) via @Column scale/precision,
 * consistent with portfolio_positions.quantity and price_bars.adj_close.
 *
 * Pure domain class - no JPA annotations (split in Phase 1).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Simulation extends BaseEntitySoftDelete {

    private UUID portfolioId;
    private UUID userId;

    // ── Request parameters ────────────────────────────────────────────────

    private int numberOfSimulations;
    private int timeHorizonDays;
    private double confidenceLevel;

    /** Null when the service derived μ from market data rather than using a caller override. */
    private Double assumedReturnPct;

    /** Null when the service derived σ from market data rather than using a caller override. */
    private Double assumedVolatilityPct;

    // ── Snapshot ──────────────────────────────────────────────────────────

    /**
     * Live portfolio value fetched from market data at run time.
     * Stored as NUMERIC(19,4) — precision=19, scale=4 — matching price_bars and positions.
     */
    private BigDecimal currentPortfolioValue;

    // ── Result ────────────────────────────────────────────────────────────

    /**
     * Full GBM result stored as JSONB.
     * Shape: { statistics, outcomes, percentileSeries, allPaths, distribution }
     * Mirrors the frontend SimulationResult type exactly for zero-transform serialisation.
     */
    private SimulationPayload resultPayload;

    // ── Status ──────────────────────────────────────────────────────────────

    private SimulationStatus status;

    // ── Identity ──────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Simulation that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Simulation{portfolioId=%s, userId=%s, paths=%d, horizon=%dd}"
                .formatted(portfolioId, userId, numberOfSimulations, timeHorizonDays);
    }
}