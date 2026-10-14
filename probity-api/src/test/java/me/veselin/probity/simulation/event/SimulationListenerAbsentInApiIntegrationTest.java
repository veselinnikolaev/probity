package me.veselin.probity.simulation.event;

import me.veselin.probity.simulation.BaseSimulationIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-D3 — the api context must not register the simulation listener.
 *
 * <p>The api depends on probity-worker (for the producer), so
 * {@link SimulationRequestedEventConsumer} is on the api classpath and its {@code @KafkaListener}
 * would otherwise be registered by component scan. The api ships
 * {@code probity.simulation.worker.enabled: false}, so the bean — and therefore the listener
 * container — must be absent from the api context. A container here would put a running api into
 * the {@code probity-simulation-worker} consumer group, which is the defect PR-D3 fixes.
 *
 * <p>Asserts on the runtime artifact (the endpoint registry) and the bean, not on a property
 * string, so removing the gate turns this red: without it the api context registers the
 * container and both assertions fail.
 */
class SimulationListenerAbsentInApiIntegrationTest extends BaseSimulationIntegrationTest {

    @Autowired
    private KafkaListenerEndpointRegistry listenerEndpointRegistry;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("PR-D3: the api context registers no @KafkaListener container for simulation-requested")
    void apiContextHasNoSimulationListenerContainer() {
        assertNull(
                listenerEndpointRegistry.getListenerContainer(SimulationRequestedEventConsumer.LISTENER_ID),
                () -> "the api context must register no @KafkaListener container under id="
                        + SimulationRequestedEventConsumer.LISTENER_ID
                        + "; a container here means a running api joins the simulation consumer group. "
                        + "registered ids=" + listenerEndpointRegistry.getListenerContainerIds());

        assertTrue(
                !listenerEndpointRegistry.getListenerContainerIds()
                        .contains(SimulationRequestedEventConsumer.LISTENER_ID),
                () -> "no listener container id may be " + SimulationRequestedEventConsumer.LISTENER_ID
                        + " in the api; registered ids=" + listenerEndpointRegistry.getListenerContainerIds());

        assertEquals(
                0,
                applicationContext.getBeanNamesForType(SimulationRequestedEventConsumer.class).length,
                "the SimulationRequestedEventConsumer bean itself must not exist in the api context");
    }
}
