package me.veselin.probity.simulation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.veselin.probity.common.audit.BaseEntitySoftDelete;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA entity for Simulation.
 * This is the persistence layer representation of the domain Simulation.
 */
@Entity
@Table(
        name = "simulations",
        indexes = {
                @Index(name = "idx_simulations_portfolio_id", columnList = "portfolio_id, created_at DESC"),
                @Index(name = "idx_simulations_user_id", columnList = "user_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationJpaEntity extends BaseEntitySoftDelete {

    @Column(name = "portfolio_id", nullable = false, updatable = false)
    private UUID portfolioId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // ── Request parameters ────────────────────────────────────────────────

    @Column(name = "number_of_simulations", nullable = false, updatable = false)
    private int numberOfSimulations;

    @Column(name = "time_horizon_days", nullable = false, updatable = false)
    private int timeHorizonDays;

    @Column(name = "confidence_level", nullable = false, updatable = false)
    private double confidenceLevel;

    /** Null when the service derived μ from market data rather than using a caller override. */
    @Column(name = "assumed_return_pct", updatable = false)
    private Double assumedReturnPct;

    /** Null when the service derived σ from market data rather than using a caller override. */
    @Column(name = "assumed_volatility_pct", updatable = false)
    private Double assumedVolatilityPct;

    // ── Snapshot ──────────────────────────────────────────────────────────

    /**
     * Live portfolio value fetched from market data at run time.
     * Stored as NUMERIC(19,4) — precision=19, scale=4 — matching price_bars and positions.
     */
    @Column(name = "current_portfolio_value", nullable = false, updatable = false,
            precision = 19, scale = 4)
    private BigDecimal currentPortfolioValue;

    // ── Result ────────────────────────────────────────────────────────────

    /**
     * Full GBM result stored as JSONB.
     * Shape: { statistics, outcomes, percentileSeries, allPaths, distribution }
     * Mirrors the frontend SimulationResult type exactly for zero-transform serialisation.
     * NULL for PENDING/PROCESSING simulations that haven't completed yet.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", updatable = false,
            columnDefinition = "jsonb")
    private me.veselin.probity.simulation.domain.SimulationPayload resultPayload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimulationJpaEntity that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
