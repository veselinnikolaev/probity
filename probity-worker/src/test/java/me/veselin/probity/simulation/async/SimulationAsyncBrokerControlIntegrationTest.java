package me.veselin.probity.simulation.async;

import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Positive control for the whole async slice.
 *
 * <p>If this test fails, every other result in {@code me.veselin.probity.simulation.async} is
 * meaningless: a broken broker, a listener that never started, a claim UPDATE that does not
 * match, or a worker that claims but cannot execute would all look identical from the outside.
 *
 * <p>It used to be pinned at {@code PROCESSING}, on the reasoning that the claim is all this
 * test was about and {@link SimulationAsyncDuplicateDeliveryIntegrationTest} owned the
 * execution half. That reasoning turned out to hide two defects: on {@code master} the row
 * sat at {@code PROCESSING} because the worker claimed, failed to claim again, and returned
 * without executing — so a pipeline that could not run a single simulation passed this control.
 * It now asserts the full path through to a {@code COMPLETED} row carrying its result, which
 * is the property that was actually worth protecting.
 */
class SimulationAsyncBrokerControlIntegrationTest extends BaseAsyncSimulationIntegrationTest {

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        AsyncKafkaTestSupport.registerAsyncBroker(registry, true, "broker-control");
    }

    @Test
    @DisplayName("control: broker, listener, claim and execution all work end to end")
    void validEventIsDeliveredAndTheRowIsCompletedWithItsResult() throws Exception {
        UUID id = seedPendingSimulation();

        publish(validEventFor(id), id);

        // Nothing but the consumer's conditional UPDATE can move a row off PENDING, and
        // nothing but a successful execution can move it to COMPLETED with a payload. Both
        // together mean broker -> listener -> claim -> execute -> persist all work.
        Simulation completed = awaitStatus(id, Set.of(SimulationStatus.COMPLETED),
                Duration.ofSeconds(45));

        assertNotNull(completed.getResultPayload(),
                () -> "the row reached COMPLETED with no result attached. That is the "
                        + "lost-result defect: status committed, computation discarded. "
                        + diagnostics(id, completed));

        String consumer = describeConsumer();
        assertTrue(consumer.contains("running=true")
                        && consumer.contains(AsyncKafkaTestSupport.sourceTopicFor("broker-control") + "-0"),
                () -> "the simulation listener should be running and holding partition 0 of "
                        + AsyncKafkaTestSupport.sourceTopicFor("broker-control") + ", but was: " + consumer);
    }
}
