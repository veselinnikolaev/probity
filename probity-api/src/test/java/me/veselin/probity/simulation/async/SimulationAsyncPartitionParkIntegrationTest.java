package me.veselin.probity.simulation.async;

import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;
import me.veselin.probity.simulation.event.SimulationRequestedEventConsumer;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-D0 - an unclaimable delivery must park <em>one partition</em>, not the whole consumer.
 *
 * <p>Replaces the pre-PR-D0 {@code ack.nack(NACK_BACKOFF)}: a nack paused every partition the
 * consumer owned for {@code max(nackDuration, pollTimeout)}, so a single record that keeps
 * finding a fresh {@code PROCESSING} row stalled every sibling partition — the whole worker —
 * once per cycle. Today the consumer seek back to the record and pauses only the partition
 * that owns it; sibling partitions keep flowing.
 *
 * <p>Staging makes the fence unambiguous: the {@code stalled} row is written {@code PROCESSING}
 * with a fresh {@code updated_at} (a claim that cannot be taken for one lease), parked on
 * partition 0; the {@code live} row is {@code PENDING} and therefore claimable, and it is
 * forced onto partition 1. With concurrency 1 the single consumer owns both partitions, so a
 * whole-consumer pause would make delivering {@code live} impossible.
 *
 * <p>The parked record's offset must also never be committed: an ack would destroy the
 * redelivery the park cycle depends on. That is probed directly on the group.
 */
class SimulationAsyncPartitionParkIntegrationTest extends BaseAsyncSimulationIntegrationTest {

    /**
     * Long park backoff: long enough that the whole test runs while partition 0 is still
     * parked, so the assertions observe a genuinely paused partition rather than a racing one.
     */
    private static final String PARK_BACKOFF = "PT60S";

    @Autowired
    private KafkaListenerEndpointRegistry listenerEndpointRegistry;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        AsyncKafkaTestSupport.registerAsyncBroker(registry, true, "prd0-partition-park");
        // Two partitions so the paused partition and its sibling are distinct objects.
        registry.add("probity.kafka.simulation-topic-partitions", () -> 2);
        registry.add("probity.simulation.unclaimable-backoff", () -> PARK_BACKOFF);
    }

    @Test
    @DisplayName("PR-D0: parking the partition of an unclaimable record does not stall its sibling")
    void parkedPartition_doesNotStallSiblingPartition() throws Exception {
        UUID stalled = seedPendingSimulation();
        // A freshly-claimed, unclaimable row: PROCESSING written moments ago. Only the lease
        // (12m default here) can make it reclaimable, so it parks — it must not run.
        simulationRepository.updateStatus(stalled, SimulationStatus.PROCESSING);
        UUID live = seedPendingSimulation();

        String topic = currentSourceTopic();
        TopicPartition tp0 = new TopicPartition(topic, 0);
        TopicPartition tp1 = new TopicPartition(topic, 1);

        // Wait for the group's single consumer to own BOTH partitions before publishing, so
        // the parked partition and its sibling are guaranteed to be under one consumer.
        await().atMost(Duration.ofSeconds(30))
                .until(() -> {
                    Collection<TopicPartition> assigned = container().getAssignedPartitions();
                    return assigned != null && assigned.containsAll(List.of(tp0, tp1));
                });

        // Force the unclaimable record onto partition 0 and wait for the park to take effect.
        kafkaTemplate.send(topic, 0, stalled.toString(), validEventFor(stalled)).get(15, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(30)).until(() -> container().isPartitionPaused(tp0));

        // The parked record must never be acknowledged: committing its offset would destroy
        // the redelivery that the park cycle depends on.
        Optional<Long> parkedOffsetCommitted = committedOffsetFor(tp0);

        // Now deliver a claimable record on the sibling partition. Under the old whole-consumer
        // nack this could not run; under the per-partition park it must complete while tp0 sits.
        kafkaTemplate.send(topic, 1, live.toString(), validEventFor(live)).get(15, TimeUnit.SECONDS);
        Simulation settled = awaitStatus(live, Set.of(SimulationStatus.COMPLETED),
                Duration.ofSeconds(30),
                () -> "PR-D0 - an unclaimable delivery must stop its own partition only. The "
                        + "claimable record on the sibling partition was not executed while "
                        + "partition 0 was parked, which is what the pre-PR-D0 whole-consumer "
                        + "nack did. " + diagnostics(live, null));

        assertEquals(SimulationStatus.COMPLETED, settled.getStatus(),
                () -> diagnostics(live, settled));
        assertEquals(1, executionAttempts(),
                () -> "only the live record may execute; the parked record must not be claimed. "
                        + diagnostics(live, settled));
        assertEquals(SimulationStatus.PROCESSING, statusOf(stalled).orElseThrow(),
                () -> "the parked record must leave its row untouched: a fresh claim is not "
                        + "reclaimable within the park window. " + diagnostics(stalled, null));

        // The park scope, asserted directly on the container: tp0 paused, tp1 free, and tp0
        // still paused after the sibling completed.
        assertTrue(container().isPartitionPaused(tp0),
                () -> "partition 0 must still be parked when the sibling completes. "
                        + diagnostics(live, settled));
        assertFalse(container().isPartitionPaused(tp1),
                () -> "the park is per-partition; partition 1 must never be paused by the park "
                        + "of partition 0. " + diagnostics(live, settled));

        // And the parked record's offset must never be committed.
        Optional<Long> after = committedOffsetFor(tp0);
        assertTrue(after.isEmpty() || after.get() == 0L,
                () -> "the parked record must not be acknowledged; committing its offset would "
                        + "destroy the redelivery the park depends on. committed=" + after
                        + " " + diagnostics(live, settled));
    }

    private MessageListenerContainer container() {
        MessageListenerContainer container =
                listenerEndpointRegistry.getListenerContainer(SimulationRequestedEventConsumer.LISTENER_ID);
        assertNotNull(container,
                "no Kafka listener container registered under id="
                        + SimulationRequestedEventConsumer.LISTENER_ID);
        return container;
    }

    /**
     * The group's committed offset for one specific partition (the base's
     * {@link #committedOffset(String)} only probes partition 0 of the source topic).
     */
    private Optional<Long> committedOffsetFor(TopicPartition partition) throws Exception {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, AsyncKafkaTestSupport.KAFKA.getBootstrapServers());
        try (Admin admin = Admin.create(props)) {
            Map<TopicPartition, OffsetAndMetadata> offsets = admin.listConsumerGroupOffsets(currentGroupId())
                    .partitionsToOffsetAndMetadata()
                    .get(10, TimeUnit.SECONDS);
            OffsetAndMetadata offset = offsets.get(partition);
            return offset == null ? Optional.empty() : Optional.of(offset.offset());
        }
    }
}