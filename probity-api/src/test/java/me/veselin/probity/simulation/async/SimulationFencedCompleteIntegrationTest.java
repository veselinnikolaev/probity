package me.veselin.probity.simulation.async;

import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationPayload;
import me.veselin.probity.simulation.domain.SimulationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static me.veselin.probity.simulation.async.AsyncKafkaTestSupport.registerDormantListener;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3b - the worker's write-back is <strong>fenced</strong>, and it actually persists what
 * it wrote.
 *
 * <p>These replace the master-compatible hazard probe that Step 2 shipped as
 * {@code lostClaim_stillOverwritesTheRowOnMaster}. That probe used master's token-less
 * {@code complete(id, value, payload)} to reproduce the hazard and asserted that a stale
 * worker's write-back <em>was</em> accepted — green on master, with green being the finding.
 * It is deleted here, not re-broken, because a test that asserts the bug must not survive
 * the fix.
 *
 * <p>Its replacement inverts the assertion against the fenced API, and adds a control
 * proving the rejection is the <em>fence</em> and not a predicate that matches nothing.
 *
 * <p>Both defects below were independent. The fencing test also had to make the result
 * survive the write, because with {@code result_payload} and
 * {@code current_portfolio_value} excluded from the {@code UPDATE} (F14) a rejected
 * write and an accepted-but-discarded write look identical from the outside.
 *
 * <p>No Kafka and no listener: this is the repository's write-back path in isolation.
 * Whether a <em>successful</em> write publishes a completion event is the service's
 * concern, not the repository's, and is asserted end-to-end by T2.
 */
class SimulationFencedCompleteIntegrationTest extends BaseAsyncSimulationIntegrationTest {

    /** Short enough that a sleep can outlast it, long enough to be a real lease. */
    private static final Duration LEASE = Duration.ofMillis(400);

    /** Distinctive so an assertion can prove which payload was written. */
    private static final double WORKER_A_VALUE = 111.0;
    private static final double WORKER_B_VALUE = 222.0;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registerDormantListener(registry);
    }

    // ── fencing ──────────────────────────────────────────────────────────────

    /**
     * The inverted probe. A worker whose lease lapsed and whose row a peer reclaimed must not
     * be able to write back: its write affects zero rows, changes nothing, and the reclaiming
     * worker's own write then succeeds — which is what proves the rejection was the fence and
     * not a predicate that never matches.
     */
    @Test
    @DisplayName("T3b: a write-back presenting a stale fence token is rejected and changes nothing")
    void completeWithAStaleFenceToken_isRejectedAndChangesNothing() {
        UUID id = seedPendingSimulation();

        // ── Worker A claims ───────────────────────────────────────────────────
        Instant fenceA = simulationRepository.claimForProcessing(id, LEASE)
                .orElseThrow(() -> new AssertionError("worker A must be able to claim a PENDING row"));

        // ── A's lease lapses ──────────────────────────────────────────────────
        // In production this is a crash, a GC pause, or a wedged execution. Here the
        // only thing needed is for updated_at to fall outside the lease window.
        // Observed, never mutated: the wait must not itself claim the row.
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> simulationRepository.findById(id)
                        .map(Simulation::getUpdatedAt)
                        .map(updatedAt -> updatedAt.isBefore(Instant.now().minus(LEASE)))
                        .orElse(false));

        // ── Worker B reclaims ─────────────────────────────────────────────────
        Instant fenceB = simulationRepository.claimForProcessing(id, LEASE)
                .orElseThrow(() -> new AssertionError(
                        "worker B must be able to claim the row once A's lease has lapsed"));

        assertEquals(SimulationStatus.PROCESSING,
                simulationRepository.findById(id).orElseThrow().getStatus());
        assertNull(simulationRepository.findById(id).orElseThrow().getResultPayload(),
                "B has not produced a result yet");

        // ── A's late write-back is refused ────────────────────────────────────
        BigDecimal staleValue = new BigDecimal("2160.00");
        Simulation rejected = simulationRepository
                .complete(id, staleValue, payload(WORKER_A_VALUE), fenceA)
                .orElse(null);

        assertNull(rejected,
                () -> "a worker presenting a superseded fence token must not complete the row. "
                        + "A claimed at " + fenceA + ", B reclaimed at " + fenceB + ", and A's "
                        + "write-back was accepted anyway — so B is now running against a row "
                        + "that claims to be finished, carrying A's stale result. "
                        + diagnostics(id, simulationRepository.findById(id).orElseThrow()));

        Simulation afterRejection = simulationRepository.findById(id).orElseThrow();
        assertEquals(SimulationStatus.PROCESSING, afterRejection.getStatus(),
                () -> "the rejected write must leave the status alone — B owns the row. "
                        + diagnostics(id, afterRejection));
        assertNull(afterRejection.getResultPayload(),
                () -> "the rejected write must persist no payload. " + diagnostics(id, afterRejection));
        assertEquals(0, BigDecimal.ZERO.compareTo(afterRejection.getCurrentPortfolioValue()),
                () -> "the rejected write must not persist a portfolio value either. "
                        + diagnostics(id, afterRejection));

        // ── the control: the predicate does match, on the current fence ───────
        Simulation accepted = simulationRepository
                .complete(id, staleValue, payload(WORKER_B_VALUE), fenceB)
                .orElseThrow(() -> new AssertionError(
                        "the same write must succeed when presented with B's current fence token. "
                                + "If it does not, the rejection above proves nothing: it would be "
                                + "a predicate that never matches, not a fence. fenceA=" + fenceA
                                + " fenceB=" + fenceB));

        assertEquals(SimulationStatus.COMPLETED, accepted.getStatus());
        assertEquals(WORKER_B_VALUE, accepted.getResultPayload().statistics().expectedFinalValue(),
                "the persisted result must be B's, not the stale worker's A");
    }

    /**
     * F14 - the result the worker computed must reach the database.
     *
     * <p>{@code SimulationJpaEntity.resultPayload} and {@code currentPortfolioValue} are both
     * declared {@code updatable = false}, so the {@code save()} the old
     * {@code SimulationRepository.complete} issued omitted both. A COMPLETED row therefore
     * carried no payload and no portfolio value: the worker's entire result, silently
     * discarded while the row claimed success.
     *
     * <p>The synchronous path escaped this because it INSERTs the row with the payload
     * already attached rather than updating a PENDING one, which is why the existing suite
     * was green. Only {@code complete()} — the async path — hit it.
     *
     * <p>The returned domain object is not evidence: the old implementation mapped the same
     * mutated instance it saved, so it showed a payload even when nothing reached the
     * database. The new one re-reads, and the assertion below re-reads again.
     */
    @Test
    @DisplayName("T3b: complete() must persist the result payload and portfolio value")
    void complete_persistsTheResultPayloadAndPortfolioValue() {
        UUID id = seedPendingSimulation();
        Instant fence = simulationRepository.claimForProcessing(id, LEASE)
                .orElseThrow(() -> new AssertionError("the row must be claimable before it can be completed"));

        BigDecimal value = new BigDecimal("2160.00");
        SimulationPayload result = payload(WORKER_A_VALUE);
        Simulation returned = simulationRepository.complete(id, value, result, fence).orElseThrow();

        Simulation reread = simulationRepository.findById(id).orElseThrow();

        assertNotNull(reread.getResultPayload(),
                () -> "complete() set status=COMPLETED but the re-read row has a null "
                        + "resultPayload, so the computation was thrown away. The entity "
                        + "declares resultPayload updatable = false, which excludes it from "
                        + "the UPDATE that save() issues. " + diagnostics(id, reread));

        assertEquals(WORKER_A_VALUE,
                reread.getResultPayload().statistics().expectedFinalValue(),
                () -> "the persisted payload must be the one complete() was handed. "
                        + diagnostics(id, reread));

        assertEquals(0, value.compareTo(reread.getCurrentPortfolioValue()),
                () -> "currentPortfolioValue is also updatable = false, so the live portfolio "
                        + "value is lost the same way the payload is. " + diagnostics(id, reread));

        assertNotNull(returned.getResultPayload(),
                "complete() now re-reads from the database, so its return value is evidence too");
    }

    // ── the other two conditional writes this commit introduces ──────────────

    /**
     * The failure path's release, fenced on the same token. Releasing a row you no longer own
     * would hand a peer an actively running job back to the queue.
     */
    @Test
    @DisplayName("T3b: release returns a row to PENDING only for the worker holding the claim")
    void releaseToPending_isFencedOnTheSameToken() {
        UUID id = seedPendingSimulation();

        // Held by us: the release works and puts the row back on the queue.
        Instant mine = simulationRepository.claimForProcessing(id, LEASE).orElseThrow();
        assertTrue(simulationRepository.releaseToPending(id, mine),
                "the holder of the claim must be able to release the row");
        assertEquals(SimulationStatus.PENDING, simulationRepository.findById(id).orElseThrow().getStatus(),
                "a released row must be claimable again, or the retry budget would be spent on "
                        + "a row nobody can take");

        // A token from an earlier, already-released claim: refused.
        assertFalse(simulationRepository.releaseToPending(id, mine),
                "releasing twice with the same token must fail the second time — the first "
                        + "release moved updated_at, so the token no longer matches");

        // Claimed, lapsed, reclaimed: the superseded worker must not release.
        Instant fenceA = simulationRepository.claimForProcessing(id, LEASE).orElseThrow();
        awaitLeaseToLapse(id);
        Instant fenceB = simulationRepository.claimForProcessing(id, LEASE).orElseThrow();

        assertFalse(simulationRepository.releaseToPending(id, fenceA),
                () -> "a superseded worker must not release the row a peer now owns; that would "
                        + "hand an actively running job back to the queue for double execution. "
                        + "fenceA=" + fenceA + " fenceB=" + fenceB
                        + diagnostics(id, simulationRepository.findById(id).orElseThrow()));

        assertEquals(SimulationStatus.PROCESSING, simulationRepository.findById(id).orElseThrow().getStatus(),
                "the row must remain PROCESSING — still owned by the worker that holds the claim");
    }

    /**
     * The dead-letter recoverer's write. It has no fence token, so {@code status = 'PENDING'}
     * is its only predicate, and the {@code PROCESSING} exclusion is the load-bearing part:
     * if our own release lost its fence, a peer is running the row, and writing FAILED there
     * would fabricate a terminal failure for work still in flight.
     */
    @Test
    @DisplayName("T3b: markFailed writes FAILED only from PENDING, never from PROCESSING")
    void markFailedIfPending_writesOnlyFromPending() {
        // From PENDING — the only state every dead-letter path leaves behind.
        UUID pending = seedPendingSimulation();
        assertTrue(simulationRepository.markFailedIfPending(pending),
                "a PENDING row must be marked FAILED by the recoverer");
        assertEquals(SimulationStatus.FAILED, simulationRepository.findById(pending).orElseThrow().getStatus());

        // Idempotent: a second pass must not touch an already-terminal row.
        assertFalse(simulationRepository.markFailedIfPending(pending),
                "FAILED is terminal and authoritative — the recoverer must not rewrite it");
        assertEquals(SimulationStatus.FAILED, simulationRepository.findById(pending).orElseThrow().getStatus());

        // From PROCESSING — a peer owns it.
        UUID processing = seedPendingSimulation();
        simulationRepository.claimForProcessing(processing, LEASE).orElseThrow();
        assertFalse(simulationRepository.markFailedIfPending(processing),
                "a PROCESSING row is owned by a worker that is probably running right now; "
                        + "marking it FAILED would fabricate a terminal failure for live work");
        assertEquals(SimulationStatus.PROCESSING, simulationRepository.findById(processing).orElseThrow().getStatus());

        // From COMPLETED — the result is authoritative.
        UUID completed = seedPendingSimulation();
        Instant fence = simulationRepository.claimForProcessing(completed, LEASE).orElseThrow();
        simulationRepository.complete(completed, new BigDecimal("2160.00"), payload(WORKER_A_VALUE), fence)
                .orElseThrow();
        assertFalse(simulationRepository.markFailedIfPending(completed),
                "a COMPLETED row carries a result and must never be overwritten by a late "
                        + "dead-letter from an earlier attempt");
        assertEquals(SimulationStatus.COMPLETED, simulationRepository.findById(completed).orElseThrow().getStatus());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void awaitLeaseToLapse(UUID id) {
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> simulationRepository.findById(id)
                        .map(Simulation::getUpdatedAt)
                        .map(updatedAt -> updatedAt.isBefore(Instant.now().minus(LEASE)))
                        .orElse(false));
    }

    private static SimulationPayload payload(double expectedFinalValue) {
        return new SimulationPayload(
                new SimulationPayload.Statistics(expectedFinalValue, expectedFinalValue,
                        1.0, 1.0, expectedFinalValue),
                new SimulationPayload.Outcomes(0.0, 0.0, 0.0, 0.0),
                List.of(),
                List.of(),
                List.of());
    }
}