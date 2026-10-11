package me.veselin.probity.simulation.async;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * PR-D0 placeholder for the one redelivery path the PR-D0 suite does not yet cover: an
 * <em>unclaimable</em> record that parks a partition while the listener runs with
 * {@code concurrency > 1}.
 *
 * <p>PR-D0 replaces the whole-consumer {@code nack()} with a per-partition park/seek
 * ({@link me.veselin.probity.simulation.event.SimulationRequestedEventConsumer}) and proves it at
 * concurrency 1 in {@link SimulationAsyncPartitionParkIntegrationTest}: one unclaimable record
 * pauses its owning partition and never stalls a sibling. With more than one child container, the
 * same guarantee has to hold across children — an unclaimable record on a partition owned by child
 * A must park only that partition, partitions owned by child B must keep flowing, and the seek must
 * be issued to the child container that owns the record, not the parent.
 *
 * <p>PR-D0 deliberately leaves {@code spring.kafka.listener.concurrency} at its default (1), so
 * this scenario cannot be exercised yet. It becomes reachable once PR-D1/D2 raise concurrency or run
 * multiple replicas; implement it then. See
 * {@code docs/plans/PHASE_4_WORKER_EXTRACTION.md} §2.9 (PR-D1).
 */
@Disabled("PR-D1: reachable once listener concurrency > 1 is enabled (PR-D0 ships concurrency 1)")
class SimulationAsyncUnclaimableConcurrencyPlaceholderTest {

    @Test
    @DisplayName("PR-D1: an unclaimable record parks only its own partition when concurrency > 1")
    void unclaimableRecord_parksOwningPartitionOnly_underConcurrencyGreaterThanOne() {
        fail("Not implemented in PR-D0 - see the class Javadoc and the design note §2.9 (PR-D1).");
    }
}
