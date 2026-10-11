package me.veselin.probity.simulation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import me.veselin.probity.simulation.domain.SimulationPayload;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for SimulationJpaEntity.
 * This is the low-level JPA interface that the SimulationRepository adapter wraps.
 */
public interface SimulationJpaRepository extends JpaRepository<SimulationJpaEntity, UUID> {

    @Query("SELECT s FROM SimulationJpaEntity s WHERE s.id = :id AND s.userId = :userId AND s.deleted = false")
    Optional<SimulationJpaEntity> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * The live status of a row, as a bare {@code String} rather than an entity.
     *
     * <p>The consumer needs exactly one column to decide between "nack and redeliver" and
     * "acknowledge and skip", so this is one indexed primary-key lookup instead of loading
     * the whole aggregate. Soft-deleted rows and unknown ids are both absent, which is
     * correct: the consumer acknowledges and skips either way.
     */
    @Query("SELECT s.status FROM SimulationJpaEntity s WHERE s.id = :id AND s.deleted = false")
    Optional<String> findLiveStatusById(@Param("id") UUID id);

    @Query("SELECT s FROM SimulationJpaEntity s WHERE s.portfolioId = :portfolioId AND s.userId = :userId AND s.status = 'COMPLETED' AND s.deleted = false ORDER BY s.createdAt DESC")
    List<SimulationJpaEntity> findByPortfolioIdAndUserId(@Param("portfolioId") UUID portfolioId, @Param("userId") UUID userId);

    @Query("SELECT s FROM SimulationJpaEntity s WHERE s.userId = :userId AND s.status = :status AND s.deleted = false")
    List<SimulationJpaEntity> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") String status);

    /**
     * Atomically claims a simulation for processing.
     * Updates status to PROCESSING and updated_at to now if:
     * - status is PENDING, OR
     * - status is PROCESSING and updated_at is older than the lease threshold (stale)
     *
     * <p>{@code :now} is written verbatim and is the fence token the caller must present to
     * {@link #completeIfClaimHeld} or {@link #releaseIfClaimHeld}. It must therefore be
     * truncated to microseconds by the caller - {@code updated_at} is {@code TIMESTAMPTZ},
     * which stores microseconds, while {@code Instant} carries nanoseconds.
     *
     * <p>Being a bulk {@code @Modifying} update, it bypasses the JPA lifecycle and so does
     * not bump the {@code @Version} column. The conditional predicate is the only thing
     * making the claim atomic.
     *
     * @param id the simulation ID
     * @param processingStatus the status to set (PROCESSING)
     * @param staleThreshold threshold for considering a PROCESSING row stale
     * @param now the fence token to write into updated_at
     * @return number of rows updated (1 if claimed, 0 if not)
     */
    @Modifying
    @Transactional
    @Query("UPDATE SimulationJpaEntity s SET s.status = :processingStatus, s.updatedAt = :now " +
           "WHERE s.id = :id AND s.deleted = false " +
           "AND (s.status = 'PENDING' OR (s.status = 'PROCESSING' AND s.updatedAt < :staleThreshold))")
    int claimForProcessing(@Param("id") UUID id, @Param("processingStatus") String processingStatus,
                           @Param("staleThreshold") Instant staleThreshold, @Param("now") Instant now);

    /**
     * Fenced completion: marks the row COMPLETED and persists its result, but only for the
     * worker that still holds the claim.
     *
     * <p>Two things are fixed by making this one conditional bulk {@code UPDATE} instead of
     * a {@code findById} + {@code save}:
     *
     * <ul>
     *   <li><strong>The fence.</strong> {@code AND s.updatedAt = :fenceToken} means a worker
     *       whose lease lapsed and whose row was reclaimed affects 0 rows and stops, instead
     *       of overwriting the reclaiming worker's live row.</li>
     *   <li><strong>The lost result.</strong> {@code result_payload} and
     *       {@code current_portfolio_value} are declared {@code updatable = false} on the
     *       entity, which excludes them from the {@code UPDATE} that {@code save()} issues -
     *       so the old implementation marked rows COMPLETED while silently discarding the
     *       entire computation. Bulk JPQL updates name their columns explicitly and do not
     *       consult {@code updatable}, so the one statement that legitimately writes the
     *       result writes it, while the entity keeps its write-once guard for every path
     *       that should not.</li>
     * </ul>
     *
     * @return number of rows updated (1 if the fence still matched, 0 if the claim was lost)
     */
    @Modifying
    @Transactional
    @Query("UPDATE SimulationJpaEntity s SET s.status = :completedStatus, " +
           "s.resultPayload = :payload, s.currentPortfolioValue = :currentValue, s.updatedAt = :now " +
           "WHERE s.id = :id AND s.deleted = false " +
           "AND s.status = :processingStatus AND s.updatedAt = :fenceToken")
    int completeIfClaimHeld(@Param("id") UUID id,
                            @Param("processingStatus") String processingStatus,
                            @Param("completedStatus") String completedStatus,
                            @Param("payload") SimulationPayload payload,
                            @Param("currentValue") BigDecimal currentValue,
                            @Param("fenceToken") Instant fenceToken,
                            @Param("now") Instant now);

    /**
     * Fenced release: returns a claimed row to {@code PENDING} so a later delivery can take
     * it, but only for the worker that still holds the claim.
     *
     * <p>Called on the failure path before the exception is rethrown, so the retry budget is
     * not spent on a row nobody can claim. A 0-row result is normal and not an error: the
     * claim was lost, and a peer owns the row.
     *
     * @return number of rows updated (1 if the fence still matched, 0 otherwise)
     */
    @Modifying
    @Transactional
    @Query("UPDATE SimulationJpaEntity s SET s.status = :pendingStatus, s.updatedAt = :now " +
           "WHERE s.id = :id AND s.deleted = false " +
           "AND s.status = :processingStatus AND s.updatedAt = :fenceToken")
    int releaseIfClaimHeld(@Param("id") UUID id,
                           @Param("processingStatus") String processingStatus,
                           @Param("pendingStatus") String pendingStatus,
                           @Param("fenceToken") Instant fenceToken,
                           @Param("now") Instant now);

    /**
     * Marks a row {@code FAILED}, but only if it is still {@code PENDING}.
     *
     * <p>Deliberately <em>not</em> fenced: this is the dead-letter recoverer's write and it
     * has no fence token to present - a deserialisation failure never reaches a listener, and
     * an exhausted retry budget has already released the row. {@code status = 'PENDING'} is
     * therefore the predicate, and it is the only safe one:
     *
     * <ul>
     *   <li>every way this can fire leaves the row {@code PENDING} - retries exhausted,
     *       non-retryable exception, deserialisation failure;</li>
     *   <li>{@code PROCESSING} must be excluded, because our own release may have lost its
     *       fence, in which case a peer is running the row and writing {@code FAILED} there
     *       would fabricate a terminal failure for work still in flight;</li>
     *   <li>{@code COMPLETED} and {@code FAILED} are terminal and authoritative.</li>
     * </ul>
     *
     * @return number of rows updated (1 if the row was pending, 0 otherwise)
     */
    @Modifying
    @Transactional
    @Query("UPDATE SimulationJpaEntity s SET s.status = :failedStatus, s.updatedAt = :now " +
           "WHERE s.id = :id AND s.deleted = false AND s.status = :pendingStatus")
    int markFailedIfPending(@Param("id") UUID id,
                            @Param("pendingStatus") String pendingStatus,
                            @Param("failedStatus") String failedStatus,
                            @Param("now") Instant now);
}
