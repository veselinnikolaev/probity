package me.veselin.probity.simulation.persistence;

import lombok.RequiredArgsConstructor;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationPayload;
import me.veselin.probity.simulation.domain.SimulationStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter wrapping SimulationJpaRepository and SimulationMapper.
 * Provides domain-oriented interface for services to use.
 */
@Repository
@RequiredArgsConstructor
public class SimulationRepository {

    private final SimulationJpaRepository jpaRepository;

    private final SimulationPathsJpaRepository pathsJpaRepository;

    /**
     * Saves a new simulation.
     */
    public Simulation save(Simulation domain) {
        SimulationJpaEntity jpaEntity = SimulationMapper.toJpaEntity(domain);
        SimulationJpaEntity saved = jpaRepository.save(jpaEntity);
        return SimulationMapper.toDomain(saved);
    }

    /**
     * Completes a claimed simulation, fenced on the token its claim returned.
     *
     * <p>Since the Q9a storage split (docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §9/§10) this
     * is one <em>transaction</em> holding two writes: the conditional bulk {@code UPDATE}
     * writes the status, the trimmed result payload (summary, statistics, outcomes,
     * percentileSeries, distribution — but no paths), the portfolio value and
     * {@code updated_at}, and only while the worker still holds the claim; the paths array
     * goes to {@code simulation_paths} in that same transaction. The fence still decides
     * everything — 0 rows means the claim was lost, and the paths row is then not written
     * either, so a superseded worker can neither overwrite a peer's row nor orphan a paths
     * row beside it. If either write fails the whole transaction rolls back and the row
     * stays {@code PROCESSING}: there is no state in which a row reads
     * {@code COMPLETED} without the result that justifies it or without its paths.
     *
     * @param id the simulation ID
     * @param currentValue the current portfolio value at time of completion
     * @param payload the full simulation result payload; its {@code allPaths} are written
     *                off-row via {@link SimulationPayload#withoutAllPaths()}, everything
     *                else stays inline in {@code result_payload}
     * @param fenceToken the token {@link #claimForProcessing} returned for this worker
     * @return the persisted row as read back from the database, or empty if the claim was
     *         lost and the update matched no row
     */
    @Transactional
    public Optional<Simulation> complete(UUID id, BigDecimal currentValue, SimulationPayload payload,
                                        Instant fenceToken) {
        List<SimulationPayload.SimulatedPath> paths = payload.allPaths();
        int updated = jpaRepository.completeIfClaimHeld(
                id,
                SimulationStatus.PROCESSING.name(),
                SimulationStatus.COMPLETED.name(),
                payload.withoutAllPaths(),
                currentValue,
                fenceToken,
                now());
        // The UPDATE above is the fence: only a row this worker still owns may gain a
        // paths row, and it must be this transaction so the two halves cannot separate.
        if (updated > 0 && !paths.isEmpty()) {
            pathsJpaRepository.save(new SimulationPathsJpaEntity(id, paths));
        }
        // Re-read rather than return the values just handed in: a caller that maps the same
        // in-memory instance it saved cannot tell "written" from "silently discarded", and
        // that ambiguity is exactly what hid the lost-result defect.
        return updated > 0 ? findById(id) : Optional.empty();
    }

    /**
     * Returns a claimed simulation to {@code PENDING} so a later delivery can take it.
     *
     * <p>Fenced on the same token as {@link #complete}: if the claim was lost, a peer owns
     * the row and this affects 0 rows. Losing the release is not an error - the row simply
     * stays {@code PROCESSING} until its lease lapses.
     *
     * @return true if the row was still claimed by this worker and is now {@code PENDING}
     */
    public boolean releaseToPending(UUID id, Instant fenceToken) {
        return jpaRepository.releaseIfClaimHeld(
                id,
                SimulationStatus.PROCESSING.name(),
                SimulationStatus.PENDING.name(),
                fenceToken,
                now()) > 0;
    }

    /**
     * Marks a row {@code FAILED} if it is still {@code PENDING}. Used by the dead-letter
     * recoverer, which has no fence token to present.
     *
     * @return true if the row was pending and is now {@code FAILED}
     */
    public boolean markFailedIfPending(UUID id) {
        return jpaRepository.markFailedIfPending(
                id,
                SimulationStatus.PENDING.name(),
                SimulationStatus.FAILED.name(),
                now()) > 0;
    }

    /**
     * Atomically claims a PENDING (or stale PROCESSING) simulation for processing.
     *
     * <p>The returned {@link Instant} is the <strong>fence token</strong>: the exact value
     * written to {@code updated_at}, to be presented to {@link #complete} and
     * {@link #releaseToPending}. It is truncated to microseconds because {@code updated_at}
     * is {@code TIMESTAMPTZ} (microsecond storage) while {@code Instant} is nanosecond — an
     * untruncated token would round on write and then never match on comparison, so every
     * fenced write would silently affect zero rows.
     *
     * @param id the simulation ID
     * @param lease maximum age of a PROCESSING row to consider stale (e.g., 5 minutes)
     * @return the fence token if the row was claimed, empty if it was not claimable
     */
    public Optional<Instant> claimForProcessing(UUID id, java.time.Duration lease) {
        Instant now = now();
        int updated = jpaRepository.claimForProcessing(
                id, SimulationStatus.PROCESSING.name(), now.minus(lease), now);
        return updated > 0 ? Optional.of(now) : Optional.empty();
    }

    /**
     * The single clock reading every conditional write stamps with, at the precision the
     * column actually stores. Bulk JPQL updates bypass {@code @PreUpdate}, so each one must
     * set {@code updated_at} explicitly.
     */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Optional<Simulation> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(SimulationMapper::toDomain);
    }

    /**
     * The off-row paths of a Q9a split-written simulation, or empty when the row keeps its
     * paths inline (every row written before this change, and the sync {@code POST /run}
     * write, which Q9a deliberately leaves whole) or has none at all.
     *
     * <p>This is the storage half of the dual-source read; the worker's SimulationMapper
     * picks between this and the inline array. The response-side sampling that may further
     * bound the list happens there, never here — this returns what was stored.
     *
     * @see SimulationPayload#withoutAllPaths()
     */
    public Optional<List<SimulationPayload.SimulatedPath>> findPaths(UUID simulationId) {
        return pathsJpaRepository.findById(simulationId)
                .map(SimulationPathsJpaEntity::getPaths);
    }

    /**
     * The status of a live (not soft-deleted) row, or empty if it is gone.
     *
     * <p>Used by the consumer to choose between nacking and acknowledging when a claim
     * fails. Loading the whole aggregate to read one column would be wasteful, and the
     * {@code deleted} predicate is what keeps a soft-deleted row from reading as a runnable
     * {@code PENDING} one.
     */
    public Optional<SimulationStatus> findLiveStatus(UUID id) {
        return jpaRepository.findLiveStatusById(id)
                .map(SimulationStatus::valueOf);
    }

    public Optional<Simulation> findByIdAndUserId(UUID id, UUID userId) {
        return jpaRepository.findByIdAndUserId(id, userId)
                .map(SimulationMapper::toDomain);
    }

    public List<Simulation> findByPortfolioIdAndUserId(UUID portfolioId, UUID userId) {
        return jpaRepository.findByPortfolioIdAndUserId(portfolioId, userId).stream()
                .map(SimulationMapper::toDomain)
                .toList();
    }

    /**
     * Deletes all simulations (for test cleanup only).
     */
    public void deleteAll() {
        jpaRepository.deleteAll();
    }

    /**
     * Finds a simulation by ID and user ID, including status.
     */
    public Optional<Simulation> findByIdAndUserIdWithStatus(UUID id, UUID userId) {
        return jpaRepository.findByIdAndUserId(id, userId)
                .map(SimulationMapper::toDomain);
    }

    /**
     * Updates the status of a simulation, unconditionally.
     *
     * <p><strong>Not for the worker's write-back.</strong> This is a non-atomic
     * {@code findById} + {@code save} with no predicate, so it will happily overwrite a row
     * a peer owns and will strand a row in any state. The worker's paths use
     * {@link #complete} and {@link #releaseToPending}, which are fenced, and the dead-letter
     * recoverer uses {@link #markFailedIfPending}. This survives only for test fixtures that
     * need to place a row in a given state.
     */
    public void updateStatus(UUID id, SimulationStatus status) {
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.setStatus(status.name());
            jpaRepository.save(entity);
        });
    }

    /**
     * Finds simulations stuck in PROCESSING state for recovery.
     *
     * <p>Named for a time predicate it does not have: it returns <em>every</em> PROCESSING
     * row for the user, however fresh. Unused. PR-B replaces it with a sweeper that takes
     * an actual staleness bound; PR-A must not build on it.
     */
    public List<Simulation> findStaleProcessingSimulations(UUID userId) {
        return jpaRepository.findByUserIdAndStatus(userId, SimulationStatus.PROCESSING.name()).stream()
                .map(SimulationMapper::toDomain)
                .toList();
    }
}
