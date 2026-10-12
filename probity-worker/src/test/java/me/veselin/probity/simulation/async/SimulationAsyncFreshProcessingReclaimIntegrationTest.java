package me.veselin.probity.simulation.async;

import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T3a - a delivery that finds a <em>fresh</em> {@code PROCESSING} row must be nacked,
 * not acked, and must then reclaim and run exactly once. **The F2 proof.**
 *
 * <p>No container stop, no consumer-group rebalance, no {@code max.poll.interval.ms}
 * wait. The pre-seeded fresh {@code PROCESSING} row can only leave that state via
 * nack -&gt; broker redelivery -&gt; stale -&gt; claimed, so the assertion is unambiguous.
 *
 * <p>Mechanism under test (F2): the consumer acknowledges unconditionally when the claim
 * returns false. A row that is already {@code PROCESSING} looks exactly like "someone else
 * is on it", so the message is committed and destroyed. Had that peer died, the work is now
 * unrecoverable - the offset is committed and the record is never redelivered.
 *
 * <p>The committed consumer-group offset is the direct observable for the ack question.
 * It is reported in the failure message: on master it advances past the record that must
 * not have been acked.
 */
class SimulationAsyncFreshProcessingReclaimIntegrationTest extends BaseAsyncSimulationIntegrationTest {

    /** Consumed by PR-A. Inert on master, where the lease is hardcoded to five minutes. */
    private static final String SHORT_LEASE = "PT2S";

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        AsyncKafkaTestSupport.registerAsyncBroker(registry, true, "t3a-fresh-processing");
        registry.add("probity.simulation.claim-lease", () -> SHORT_LEASE);
    }

    @Test
    @DisplayName("T3a: fresh PROCESSING -> nack, reclaim after the lease, exactly one execution")
    void freshProcessingRow_isNackedThenReclaimedAndRunExactlyOnce() throws Exception {
        UUID id = seedPendingSimulation();
        // Simulate "a consumer claimed this and died": PROCESSING with a fresh updated_at.
        simulationRepository.updateStatus(id, SimulationStatus.PROCESSING);

        assertEquals(SimulationStatus.PROCESSING, statusOf(id).orElseThrow(),
                "fixture precondition: the row must start out claimed and fresh");

        Optional<Long> offsetBefore = committedOffset(currentGroupId());

        publish(validEventFor(id), id);

        // Bounded generously: a single nack cycle alone sleeps ~5 s
        // (max(nackDuration, pollTimeout)), then the 2 s lease must lapse before a reclaim.
        Simulation settled = awaitStatus(id, Set.of(SimulationStatus.COMPLETED),
                Duration.ofSeconds(40),
                () -> "F2 - the delivery found a fresh PROCESSING row and acknowledged it "
                        + "instead of nacking, so the record was destroyed rather than "
                        + "redelivered and the row is claimed by a consumer that does not "
                        + "exist. " + describeAck(offsetBefore));

        assertEquals(SimulationStatus.COMPLETED, settled.getStatus(),
                () -> describeAck(offsetBefore, id, settled));

        assertEquals(1, executionAttempts(),
                () -> "exactly one execution is expected after the reclaim. "
                        + describeAck(offsetBefore, id, settled));
    }

    /**
     * Builds the mechanism-naming part of a failure message: did the consumer commit a
     * record it was not entitled to acknowledge?
     *
     * <p>The group is shared across the slice and the broker is fresh per JVM, so the
     * first commit of a run shows up as {@code empty -> present}. Treating that as "no
     * commit" would invert the finding, so an absent baseline counts as "advanced".
     */
    private String describeAck(Optional<Long> offsetBefore) {
        Optional<Long> offsetAfter = committedOffset(currentGroupId());
        boolean advanced = offsetAfter.isPresent()
                && (offsetBefore.isEmpty() || offsetAfter.get() > offsetBefore.get());

        return advanced
                ? "The group COMMITTED its offset (" + offsetBefore + " -> " + offsetAfter
                + "), so the unclaimable record was destroyed instead of redelivered - F2, "
                + "ack-on-claim-failure."
                : "The group did not commit its offset (" + offsetBefore + " -> " + offsetAfter
                + "), so the record is still available for redelivery.";
    }

    private String describeAck(Optional<Long> offsetBefore, UUID id, Simulation observed) {
        return "F2 - the record was never redelivered, so the row stayed claimed by a "
                + "consumer that does not exist. " + describeAck(offsetBefore)
                + " Observed: " + diagnostics(id, observed);
    }
}
