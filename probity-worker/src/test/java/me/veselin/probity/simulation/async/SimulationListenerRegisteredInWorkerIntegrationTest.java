package me.veselin.probity.simulation.async;

import me.veselin.probity.simulation.event.SimulationRequestedEventConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PR-D3 — the worker context must register the simulation listener.
 *
 * <p>The counterpart of
 * {@link me.veselin.probity.simulation.event.SimulationListenerAbsentInApiIntegrationTest}
 * (api module): the gate must only ever turn the listener <em>off</em> for the api, never for the
 * worker. The worker ships {@code probity.simulation.worker.enabled: true}, so its context still
 * registers the {@code @KafkaListener} container under the production id and keeps consuming.
 *
 * <p>Asserts on the endpoint registry (the runtime artifact), so dropping the flag from the worker's
 * {@code application.yaml} or flipping {@code matchIfMissing} turns this red. The test profile keeps
 * {@code spring.kafka.listener.auto-startup=false}, so the container is created but not started —
 * which is exactly the registration this guards.
 */
class SimulationListenerRegisteredInWorkerIntegrationTest extends BaseAsyncSimulationIntegrationTest {

    @Autowired
    private KafkaListenerEndpointRegistry listenerEndpointRegistry;

    @Test
    @DisplayName("PR-D3: the worker context registers a @KafkaListener container for simulation-requested")
    void workerContextHasSimulationListenerContainer() {
        assertNotNull(
                listenerEndpointRegistry.getListenerContainer(SimulationRequestedEventConsumer.LISTENER_ID),
                () -> "the worker must register the @KafkaListener container under id="
                        + SimulationRequestedEventConsumer.LISTENER_ID
                        + "; without it the worker stops consuming simulation-requested. "
                        + "registered ids=" + listenerEndpointRegistry.getListenerContainerIds());
    }
}
