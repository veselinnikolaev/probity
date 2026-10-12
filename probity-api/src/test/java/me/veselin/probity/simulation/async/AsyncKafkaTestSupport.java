package me.veselin.probity.simulation.async;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Slim api-side stand-in for the worker's {@code AsyncKafkaTestSupport}.
 *
 * <p>The full support — the shared Testcontainers Kafka broker, {@code registerAsyncBroker},
 * {@code drainDlt} — moved to probity-worker with the async slice. The one test that stays
 * in the api, {@link SimulationRunAsyncRequestIntegrationTest}, exercises only the request
 * half of the flow, so it needs only the dormant-listener surface: a broker address nothing
 * can listen on and a listener that never starts.
 */
final class AsyncKafkaTestSupport {

    private AsyncKafkaTestSupport() {
    }

    /** Source topic, matches the default for {@code probity.kafka.simulation-requested-topic}. */
    static final String SOURCE_TOPIC = "simulation-requested";

    /** Dead-letter topic, matches the default for {@code probity.kafka.simulation-requested-dlt-topic}. */
    static final String DLT_TOPIC = "simulation-requested.DLT";

    /** The production default, used when nothing overrides the property. */
    static final String DEFAULT_CONSUMER_GROUP = "probity-simulation-worker";

    /**
     * A broker address nothing can be listening on.
     *
     * <p>Port 1 is privileged: binding it needs root, so on every platform this suite
     * supports the connection is refused immediately rather than hanging. Used so a
     * dormant-listener context cannot reach any cluster at all, instead of depending on
     * the absence of a broker on 9092.
     */
    static final String UNREACHABLE_BROKER = "localhost:1";

    /**
     * Keeps the listener dormant and the broker unreachable.
     *
     * <p>Needed by the request-contract test, which asserts on the untouched {@code PENDING}
     * row: no broker this context can reach, and no running listener, are established
     * absolutely rather than by accident.
     */
    static void registerDormantListener(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> UNREACHABLE_BROKER);
        registry.add("spring.kafka.listener.auto-startup", () -> false);
    }
}