package me.veselin.probity.simulation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import me.veselin.probity.simulation.domain.SimulationPayload;

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
    @Query("UPDATE SimulationJpaEntity s SET s.status = :processingStatus, s.attemptId = :attemptId, s.updatedAt = :now " +
           "WHERE s.id = :id AND s.deleted = false " +
           "AND (s.status = 'PENDING' OR (s.status = 'PROCESSING' AND s.updatedAt < :staleThreshold))")
    int claimForProcessing(@Param("id") UUID id, @Param("processingStatus") String processingStatus,
                           @Param("staleThreshold") Instant staleThreshold, @Param("now") Instant now,
                           @Param("attemptId") UUID attemptId);

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

    /**
     * Marks a pending row failed only when the exact claim attempt still owns the
     * retained attempt marker.
     */
    @Modifying
    @Transactional
    @Query("UPDATE SimulationJpaEntity s SET s.status = :failedStatus, s.updatedAt = :now " +
           "WHERE s.id = :id AND s.deleted = false AND s.status = :pendingStatus " +
           "AND s.attemptId = :attemptId")
    int markFailedForAttempt(@Param("id") UUID id,
                             @Param("pendingStatus") String pendingStatus,
                             @Param("failedStatus") String failedStatus,
                             @Param("attemptId") UUID attemptId,
                             @Param("now") Instant now);

    // ── transactional outbox ────────────────────────────────────────────────

    /**
     * Inserts one outbox row in the caller's transaction.
     *
     * <p>{@code CAST(:payloadJson AS JSONB)} is load-bearing: the driver binds a {@code String}
     * as {@code varchar}, and PostgreSQL will not implicitly cast {@code varchar} to
     * {@code jsonb} in an {@code INSERT}. Without the cast the production insert fails at
     * runtime even though the same statement works against an already-jsonb column.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO simulation_outbox (id, simulation_id, event_type, payload, created_at) " +
                   "VALUES (:outboxId, :simulationId, :eventType, CAST(:payloadJson AS text)::jsonb, now())",
          nativeQuery = true)
    void insertOutboxEvent(@Param("simulationId") UUID simulationId,
                           @Param("outboxId") UUID outboxId,
                           @Param("eventType") String eventType,
                           @Param("payloadJson") String payloadJson);

    /**
     * Locks the next unpublished, unleased outbox rows for the calling publisher.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} is what makes two concurrent publishers safe: the
     * second one skips rows the first has locked instead of blocking behind them or claiming
     * the same work. Rows whose lease has expired are claimable again.
     *
     * <p>{@code payload::text} is required: a native query maps {@code jsonb} to
     * {@code PGobject}, not {@code String}, so the explicit cast is what gives the publisher
     * something Jackson can read.
     *
     * <p>The caller must run this inside a transaction, then commit before doing any Kafka
     * I/O - the row lock must not be held while waiting for the broker.
     *
     * @return rows of {@code [id (UUID), simulation_id (UUID), payload (String)]}
     */
    @Query(value = """
            SELECT s.id, s.simulation_id, s.payload::text
              FROM simulation_outbox s
             WHERE s.published_at IS NULL
               AND (s.claimed_until IS NULL OR s.claimed_until < :now)
             ORDER BY s.created_at ASC
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Object[]> lockPendingOutboxBatch(@Param("limit") int limit, @Param("now") Instant now);

    /**
     * Leases the rows returned by {@link #lockPendingOutboxBatch} to one publisher.
     *
     * <p>The lease is a durable claim: it survives the transaction that wrote it, so the
     * publisher can commit the claim, perform the (slow, external) Kafka send, and write the
     * outcome in a later short transaction without holding row locks across the network call.
     * A row becomes claimable again when {@code claimed_until} passes.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE simulation_outbox
               SET claim_token = :token,
                   claimed_until = :claimedUntil
             WHERE id IN (:ids)
            """, nativeQuery = true)
    int claimOutboxRecords(@Param("ids") List<UUID> ids,
                           @Param("token") UUID token,
                           @Param("claimedUntil") Instant claimedUntil);

    /**
     * Marks a leased row published, fenced on the claim token.
     *
     * <p>{@code AND claim_token = :token} is the fencing token check: a publisher whose lease
     * lapsed and whose row was reclaimed by a newer publisher updates 0 rows instead of
     * overwriting the newer claim. A 0-row result is therefore a legitimate outcome, not an
     * error - it means "someone else owns this row now".
     *
     * @return number of rows updated (1 if this publisher still held the claim, 0 otherwise)
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE simulation_outbox
               SET published_at = now(),
                   error_count = 0,
                   claim_token = NULL,
                   claimed_until = NULL
             WHERE id = :id AND claim_token = :token
            """, nativeQuery = true)
    int markOutboxPublished(@Param("id") UUID id, @Param("token") UUID token);

    /**
     * Records a publication failure and releases the lease for a later retry, fenced on the
     * claim token.
     *
     * <p>The row is never dropped: {@code claimed_until} is pushed forward as exponential
     * backoff and {@code published_at} stays {@code NULL}, so the publisher will try again.
     * {@code error_count} is alerting data only - there is no attempt limit that removes work
     * from the queue.
     *
     * @return number of rows updated (1 if this publisher still held the claim, 0 otherwise)
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE simulation_outbox
               SET error_count = error_count + 1,
                   claim_token = NULL,
                   claimed_until = :retryAt
             WHERE id = :id AND claim_token = :token
            """, nativeQuery = true)
    int recordOutboxFailure(@Param("id") UUID id,
                            @Param("token") UUID token,
                            @Param("retryAt") Instant retryAt);

    /** The number of consecutive publication failures recorded for a row (alerting only). */
    @Query(value = "SELECT error_count FROM simulation_outbox WHERE id = :id", nativeQuery = true)
    Optional<Integer> findOutboxErrorCount(@Param("id") UUID id);

    // ── reconciliation sweeper (M2) ──────────────────────────────────────────

    /**
     * Q1: Finds PENDING simulations whose outbox row is marked published but whose workflow
     * was never picked up by a consumer within the grace period (lost Kafka delivery C4).
     */
    @Query(value = """
            SELECT s.id,
                   COALESCE(s.updated_at, s.created_at) AS state_since,
                   o.id                                AS outbox_id,
                   o.published_at,
                   o.claimed_until
              FROM simulations s
              JOIN simulation_outbox o ON o.simulation_id = s.id
             WHERE s.deleted = FALSE
               AND s.status = 'PENDING'
               AND COALESCE(s.updated_at, s.created_at) < now() - make_interval(secs => :pendingGraceSeconds)
               AND o.published_at IS NOT NULL
               AND (o.claimed_until IS NULL OR o.claimed_until < now())
               AND (o.last_reconciled_at IS NULL
                    OR o.last_reconciled_at < now() - make_interval(secs => :cooldownSeconds))
             ORDER BY state_since ASC
             LIMIT :batchSize
            """, nativeQuery = true)
    List<Object[]> findPendingSimulationsWithoutDelivery(
            @Param("pendingGraceSeconds") double pendingGraceSeconds,
            @Param("cooldownSeconds") double cooldownSeconds,
            @Param("batchSize") int batchSize);

    /**
     * Q2: Finds stale PROCESSING simulations with an outbox row that have exceeded their
     * processing grace window without completion (abandoned worker C5).
     */
    @Query(value = """
            SELECT s.id,
                   COALESCE(s.updated_at, s.created_at) AS state_since,
                   o.id                                AS outbox_id,
                   o.published_at,
                   o.claimed_until
              FROM simulations s
              JOIN simulation_outbox o ON o.simulation_id = s.id
             WHERE s.deleted = FALSE
               AND s.status = 'PROCESSING'
               AND COALESCE(s.updated_at, s.created_at) < now() - make_interval(secs => :processingGraceSeconds)
               AND (o.last_reconciled_at IS NULL
                    OR o.last_reconciled_at < now() - make_interval(secs => :cooldownSeconds))
             ORDER BY state_since ASC
             LIMIT :batchSize
            """, nativeQuery = true)
    List<Object[]> findStaleProcessingSimulationsWithOutbox(
            @Param("processingGraceSeconds") double processingGraceSeconds,
            @Param("cooldownSeconds") double cooldownSeconds,
            @Param("batchSize") int batchSize);

    /**
     * Q3: Finds PENDING simulations with no outbox row (alert-only, S9).
     */
    @Query(value = """
            SELECT s.id, COALESCE(s.updated_at, s.created_at) AS state_since
              FROM simulations s
              LEFT JOIN simulation_outbox o ON o.simulation_id = s.id
             WHERE s.deleted = FALSE
               AND s.status = 'PENDING'
               AND o.id IS NULL
               AND COALESCE(s.updated_at, s.created_at) < now() - make_interval(secs => :pendingGraceSeconds)
             ORDER BY state_since ASC
             LIMIT :batchSize
            """, nativeQuery = true)
    List<Object[]> findPendingSimulationsWithoutOutbox(
            @Param("pendingGraceSeconds") double pendingGraceSeconds,
            @Param("batchSize") int batchSize);

    /**
     * Q4: Finds orphan unpublished outbox rows with no simulation (alert-only, S10).
     */
    @Query(value = """
            SELECT o.id, o.simulation_id, o.created_at
              FROM simulation_outbox o
              LEFT JOIN simulations s ON s.id = o.simulation_id
             WHERE s.id IS NULL
               AND o.published_at IS NULL
               AND (o.claimed_until IS NULL OR o.claimed_until < now() - make_interval(secs => :orphanGraceSeconds))
             ORDER BY o.created_at ASC
             LIMIT :batchSize
            """, nativeQuery = true)
    List<Object[]> findOrphanOutboxRecords(
            @Param("orphanGraceSeconds") double orphanGraceSeconds,
            @Param("batchSize") int batchSize);

    /**
     * Q5: Finds outbox rows undergoing frequent reconciliation loops (alert-only, S16).
     */
    @Query(value = """
            SELECT o.id, o.simulation_id, o.reconcile_count
              FROM simulation_outbox o
             WHERE o.reconcile_count >= :alertThreshold
               AND o.last_reconciled_at > now() - make_interval(secs => :recentWindowSeconds)
             ORDER BY o.reconcile_count DESC
             LIMIT :batchSize
            """, nativeQuery = true)
    List<Object[]> findReconcileLoopRecords(
            @Param("alertThreshold") int alertThreshold,
            @Param("recentWindowSeconds") double recentWindowSeconds,
            @Param("batchSize") int batchSize);

    /**
     * Locks the outbox row for reconciliation update using SKIP LOCKED.
     */
    @Query(value = """
            SELECT id, published_at, claimed_until, last_reconciled_at, reconcile_count
              FROM simulation_outbox
             WHERE simulation_id = :simulationId
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Object[]> lockOutboxForReconciliation(@Param("simulationId") UUID simulationId);

    /**
     * Resets a published outbox row back to unpublished for re-publishing.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE simulation_outbox
               SET published_at = NULL,
                   claim_token = NULL,
                   claimed_until = NULL,
                   reconcile_count = reconcile_count + 1,
                   last_reconciled_at = now()
             WHERE id = :id
               AND published_at IS NOT NULL
               AND (claimed_until IS NULL OR claimed_until < now())
            """, nativeQuery = true)
    int resetOutboxPublishedStatus(@Param("id") UUID id);

    /**
     * Atomically resets a PROCESSING simulation to PENDING, fenced on observed state timestamp.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE simulations
               SET status = 'PENDING',
                   updated_at = now()
             WHERE id = :simulationId
               AND deleted = FALSE
               AND status = 'PROCESSING'
               AND COALESCE(updated_at, created_at) = :observedStateSince
            """, nativeQuery = true)
    int resetProcessingSimulationToPending(
            @Param("simulationId") UUID simulationId,
            @Param("observedStateSince") Instant observedStateSince);

    /** Counts simulations in given status older than grace period. */
    @Query(value = """
            SELECT COUNT(*)
              FROM simulations
             WHERE deleted = FALSE
               AND status = :status
               AND COALESCE(updated_at, created_at) < now() - make_interval(secs => :graceSeconds)
            """, nativeQuery = true)
    long countStuckSimulations(@Param("status") String status, @Param("graceSeconds") double graceSeconds);

    /** Counts unpublished outbox records. */
    @Query(value = "SELECT COUNT(*) FROM simulation_outbox WHERE published_at IS NULL", nativeQuery = true)
    long countUnpublishedOutbox();

    /** Returns age in seconds of oldest unpublished outbox record (or 0 if none). */
    @Query(value = "SELECT COALESCE(EXTRACT(EPOCH FROM (now() - MIN(created_at))), 0) FROM simulation_outbox WHERE published_at IS NULL", nativeQuery = true)
    double getOldestUnpublishedOutboxAgeSeconds();

    /** Returns maximum error count among outbox records. */
    @Query(value = "SELECT COALESCE(MAX(error_count), 0) FROM simulation_outbox", nativeQuery = true)
    int getMaxOutboxErrorCount();

    /** Returns age in seconds of oldest published outbox record (or 0 if none). */
    @Query(value = "SELECT COALESCE(EXTRACT(EPOCH FROM (now() - MIN(published_at))), 0) FROM simulation_outbox WHERE published_at IS NOT NULL", nativeQuery = true)
    double getOldestPublishedOutboxAgeSeconds();

    /** Returns maximum reconcile count among outbox records. */
    @Query(value = "SELECT COALESCE(MAX(reconcile_count), 0) FROM simulation_outbox", nativeQuery = true)
    int getMaxReconcileCount();
}
