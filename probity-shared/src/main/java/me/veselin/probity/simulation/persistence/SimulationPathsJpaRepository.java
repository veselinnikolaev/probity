package me.veselin.probity.simulation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA interface for {@link SimulationPathsJpaEntity} (Q9a storage split,
 * docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §10).
 *
 * <p>{@code findById(...)} is the only read the dual-source path needs: reassembly looks
 * paths up by simulation id and never lists the table. No custom queries on purpose —
 * the load surface stays exactly INSERT (split write) + SELECT by key (read).
 */
public interface SimulationPathsJpaRepository extends JpaRepository<SimulationPathsJpaEntity, UUID> {
}
