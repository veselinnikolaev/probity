package me.veselin.probity.simulation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.veselin.probity.simulation.domain.SimulationPayload;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for the Q9a off-row path store (docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md
 * §9/§10, owner-signed Option A): one row per split-written simulation holding what
 * {@code result_payload->'allPaths'} used to hold.
 *
 * <p>Written by {@link SimulationRepository#complete} inside the same transaction as the
 * fenced completion UPDATE, read back by the dual-source read in the worker's
 * SimulationMapper. {@link SimulationPayload#withoutAllPaths()} produces the inline half
 * that stays in {@code simulations.result_payload}.
 *
 * <p>No relationship annotations on purpose: there is deliberately no FK to
 * {@code simulations} — V19__simulation_paths.sql explains why (the load-test reset
 * TRUNCATEs {@code simulations} on its own). Deleting a simulation therefore leaves this
 * row behind; production only soft-deletes, and hard deletes happen only in the
 * load-test reset and test cleanup, neither of which reads this table whole.
 *
 * <p>Standalone (not BaseEntity): assigned PK instead of a generated one, and a single
 * {@code created_at} instead of BaseEntity's version/updated_at pair — the table spec in
 * §10 lists exactly these three columns. The {@code @PrePersist} created_at mechanism
 * matches BaseEntity.
 */
@Entity
@Table(name = "simulation_paths")
@Getter
@Setter
@NoArgsConstructor
public class SimulationPathsJpaEntity {

    /** The simulation this row belongs to — PK shaped like {@code simulations.id}. */
    @Id
    @Column(name = "simulation_id", nullable = false, updatable = false)
    private UUID simulationId;

    /**
     * The path array exactly as it used to be serialised inside {@code result_payload}:
     * JSONB {@code [{pathId, values:[{dayIndex,value}]}]}. A non-null empty array for a
     * payload written whole (no split); this table only ever holds a non-empty list.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "paths", nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<SimulationPayload.SimulatedPath> paths;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Insert shape: createdAt is filled by {@link #onCreate()} at persist time. */
    public SimulationPathsJpaEntity(UUID simulationId, List<SimulationPayload.SimulatedPath> paths) {
        this.simulationId = simulationId;
        this.paths = paths;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimulationPathsJpaEntity that)) return false;
        return simulationId != null && simulationId.equals(that.simulationId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
