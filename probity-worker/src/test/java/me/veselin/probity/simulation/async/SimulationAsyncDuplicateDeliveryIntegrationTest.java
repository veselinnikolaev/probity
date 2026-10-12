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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * T2 - duplicate delivery must execute exactly once. **The F1 canary.**
 *
 * <p>Publishes the same {@code SimulationRequestedEvent} twice, keyed on the same
 * simulation id, and requires the row to reach {@code COMPLETED} with the execution
 * dependency invoked exactly once.
 *
 * <p>Mechanism under test (F1): the consumer claims the row and then calls a service
 * that claims it <em>again</em>. The second claim matches neither {@code status='PENDING'}
 * nor stale {@code PROCESSING}, updates zero rows, and the method returns through a
 * dummy-DTO branch without executing anything - while the consumer logs
 * "Simulation completed successfully" and acks. So the row never leaves {@code PROCESSING}
 * and the execution count is zero.
 *
 * <p>Asserts on state and invocation count, never on logs: today's log actively lies.
 */
class SimulationAsyncDuplicateDeliveryIntegrationTest extends BaseAsyncSimulationIntegrationTest {

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        AsyncKafkaTestSupport.registerAsyncBroker(registry, true, "t2-duplicate-delivery");
    }

    @Test
    @DisplayName("T2: two deliveries of the same event run the simulation exactly once")
    void duplicateDelivery_completesTheRowAndExecutesExactlyOnce() throws Exception {
        UUID id = seedPendingSimulation();

        publish(validEventFor(id), id);
        publish(validEventFor(id), id);

        // Bounded wait, then a state assertion. On master the wait is what expires: the
        // row is claimed by the first delivery and nothing ever moves it again.
        Simulation settled = awaitStatus(id, Set.of(SimulationStatus.COMPLETED), Duration.ofSeconds(25));

        assertEquals(SimulationStatus.COMPLETED, settled.getStatus(),
                () -> "F1 - the row was claimed but never finished. A claimed row that never "
                        + "leaves PROCESSING is the signature of the consumer's double-claim "
                        + "swallowing the run. " + diagnostics(id, settled));

        Simulation done = simulationRepository.findById(id).orElseThrow();
        assertNotNull(done.getResultPayload(),
                () -> "COMPLETED with a null payload means the status was written without a "
                        + "result. " + diagnostics(id, done));

        assertEquals(1, executionAttempts(),
                () -> "the simulation must run exactly once no matter how many times the event "
                        + "is delivered. " + diagnostics(id, done));
    }
}
