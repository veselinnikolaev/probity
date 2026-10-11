package me.veselin.probity.simulation.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-D0 config invariants, asserted against the production {@code application.yaml} exactly
 * as it ships — no test overrides, no Spring context, no broker.
 *
 * <p>The central invariant (Phase 4 note §2.9) is:
 * <pre>
 *   claim-lease &gt; max.poll.interval.ms &gt;= slowest legitimate run
 * </pre>
 * The lease is what makes a crashed consumer's row reclaimable; {@code max.poll.interval.ms}
 * is what makes the broker classify a live-but-slow consumer as dead. If the lease ever fell
 * at or below the poll interval, a live worker would be fenced out and its row reclaimed
 * while it was still running. So the ordering is asserted, not just the values.
 *
 * <p>Because the values carry defaults through {@code ${...:default}} placeholders, this
 * test resolves them through the same {@link StandardEnvironment} machinery Boot uses, so a
 * stripped environment (no {@code SIMULATION_*}) still sees exactly what ships.
 */
class SimulationWorkerConfigInvariantTest {

    private StandardEnvironment productionEnvironment() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("production-application", new ClassPathResource("application.yaml"));
        assertTrue(!sources.isEmpty(),
                "application.yaml must contain at least one YAML document");
        sources.forEach(source -> environment.getPropertySources().addLast(source));
        return environment;
    }

    private Duration durationOf(StandardEnvironment environment, String property, String what) {
        String raw = environment.getProperty(property);
        assertNotNull(raw, what + " (" + property + ") must be defined in application.yaml");
        return DurationStyle.detectAndParse(raw);
    }

    @Test
    void claimLease_staysStrictlyAboveMaxPollInterval() throws Exception {
        StandardEnvironment environment = productionEnvironment();

        Duration lease = durationOf(environment, "probity.simulation.claim-lease",
                "the claim lease");
        String pollRaw = environment.getProperty("spring.kafka.consumer.properties.max.poll.interval.ms");
        assertNotNull(pollRaw,
                "max.poll.interval.ms (spring.kafka.consumer.properties.max.poll.interval.ms) "
                        + "must be defined in application.yaml");
        long pollMillis = Long.parseLong(pollRaw);

        assertEquals(Duration.ofMinutes(12), lease,
                "PR-D0 pins the claim lease to 12m. A shorter lease risks fencing a live "
                        + "worker out of its own row mid-run.");
        assertEquals(600_000L, pollMillis,
                "PR-D0 pins max.poll.interval.ms to 10m (600000 ms); it is the lease's "
                        + "ceiling, not its rival.");
        assertTrue(lease.toMillis() > pollMillis,
                "claim-lease (" + lease + ") must stay strictly above max.poll.interval.ms ("
                        + Duration.ofMillis(pollMillis) + "): a lease at or below the poll "
                        + "interval lets a peer reclaim a row whose owner is merely slow, not dead.");
    }

    @Test
    void valueDeserializer_isWrappedForDltRouting() throws Exception {
        StandardEnvironment environment = productionEnvironment();

        assertEquals("org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
                environment.getProperty("spring.kafka.consumer.value-deserializer"),
                "the value deserializer must be the ErrorHandlingDeserializer wrapper: a poison "
                        + "payload then surfaces as a listener-level DeserializationException the "
                        + "DefaultErrorHandler can dead-letter, instead of a Fetcher-level "
                        + "SerializationException it refuses, which wedges the partition forever.");
        assertEquals("org.springframework.kafka.support.serializer.JsonDeserializer",
                environment.getProperty(
                        "spring.kafka.consumer.properties.spring.deserializer.value.delegate.class"),
                "the wrapper's delegate must remain the production JsonDeserializer.");
        assertNotNull(environment.getProperty(
                        "spring.kafka.consumer.properties.spring.json.trusted.packages"),
                "the trusted-packages allowlist must survive the wrapper change.");
    }

    @Test
    void simulationTopic_hasSixPartitions() throws Exception {
        StandardEnvironment environment = productionEnvironment();

        assertEquals("6", environment.getProperty("probity.kafka.simulation-topic-partitions"),
                "PR-D0 raises the partition ceiling from 1 to 6 (Phase 4 note §2.9, Q2). Topic "
                        + "partition count is fixed at creation and cannot be reduced later, so the "
                        + "production default must already be the target.");
    }

    @Test
    void unclaimableBackoff_isPositiveAndWellUnderTheLease() throws Exception {
        StandardEnvironment environment = productionEnvironment();

        Duration backoff = durationOf(environment, "probity.simulation.unclaimable-backoff",
                "the unclaimable backoff");
        Duration lease = durationOf(environment, "probity.simulation.claim-lease",
                "the claim lease");

        assertEquals(Duration.ofSeconds(1), backoff, "PR-D0 default is 1s");
        assertTrue(backoff.compareTo(Duration.ZERO) > 0, "the backoff must be positive");
        assertTrue(backoff.toMillis() < lease.toMillis(),
                "the backoff (" + backoff + ") must stay well under the lease (" + lease
                        + "), else a row would go stale and be reclaimed while its partition "
                        + "is still parked, and the redelivery would run as a plain duplicate.");
    }
}