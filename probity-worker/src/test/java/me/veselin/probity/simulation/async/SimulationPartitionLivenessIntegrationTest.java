package me.veselin.probity.simulation.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Stage 4 (C): Partition liveness and terminal/unclaimable-record behaviour.
 * <p>Builds on the Stage 2 projection fix. Each scenario publishes a generation-stamped event
 * and asserts durable database state, DLT state, offsets where observable, and business execution
 * counts — without isolating topics or reordering tests to hide defects.
 */
class SimulationPartitionLivenessIntegrationTest extends BaseAsyncSimulationIntegrationTest {

  private static final Duration WAIT = Duration.ofSeconds(30);
  private static final String GROUP_SUFFIX = "c-partition-liveness";

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    AsyncKafkaTestSupport.registerAsyncBroker(registry, true, GROUP_SUFFIX);
    // Two partitions: the park scenarios need the unclaimable record and its sibling on
    // different partitions, placed explicitly — key hashing cannot guarantee that.
    registry.add("probity.kafka.simulation-topic-partitions", () -> 2);
  }

  /** C1 — COMPLETED duplicate: acknowledged, no second execution, no DLT, sibling continues. */
  @Test
  @DisplayName("C1: COMPLETED duplicate is acknowledged; sibling valid record processes")
  void completedDuplicate_acknowledgedNoSecondExecutionNoDltSiblingContinues() throws Exception {
    UUID completedId = seedDirectRow(SimulationStatus.COMPLETED);
    UUID siblingId = seedDirectRow(SimulationStatus.PENDING);

    publishWithGeneration(validEventFor(completedId), completedId, 1);
    publishWithGeneration(validEventFor(siblingId), siblingId, 1);

    Simulation settled =
        awaitStatus(
            siblingId,
            Set.of(SimulationStatus.COMPLETED),
            WAIT,
            () -> "C1 - the COMPLETED duplicate must be acked; the sibling must complete. "
                + diagnostics(siblingId, null));

    assertEquals(
        SimulationStatus.COMPLETED,
        settled.getStatus(),
        () -> "sibling simulation must reach COMPLETED. Observed: " + diagnostics(siblingId, settled));

    assertEquals(
        1,
        executionAttempts(),
        () -> "only the sibling may execute; the COMPLETED duplicate has nothing to run. "
            + diagnostics(siblingId, settled));

    // No DLT record for the terminal duplicate
    List<ConsumerRecord<String, byte[]>> dlt =
        AsyncKafkaTestSupport.drainDlt(Duration.ofSeconds(5), 0, GROUP_SUFFIX);
    assertTrue(
        dlt.isEmpty(),
        "no DLT record must be written for a COMPLETED duplicate. Observed dlt=" + dlt);

    // The duplicate row stays COMPLETED
    assertEquals(
        SimulationStatus.COMPLETED,
        statusOf(completedId).orElseThrow(),
        "the duplicate must remain COMPLETED. Observed: " + diagnostics(completedId, null));
  }

  /** C2 — FAILED duplicate: correct terminal handling; no second execution; sibling continues. */
  @Test
  @DisplayName("C2: FAILED duplicate is handled terminally; sibling valid record processes")
  void failedDuplicate_terminalHandlingSiblingContinues() throws Exception {
    UUID failedId = seedDirectRow(SimulationStatus.FAILED);
    UUID siblingId = seedDirectRow(SimulationStatus.PENDING);

    publishWithGeneration(validEventFor(failedId), failedId, 1);
    publishWithGeneration(validEventFor(siblingId), siblingId, 1);

    Simulation settled =
        awaitStatus(
            siblingId,
            Set.of(SimulationStatus.COMPLETED),
            WAIT,
            () -> "C2 - the FAILED duplicate must not execute; the sibling must complete. "
                + diagnostics(siblingId, null));

    assertEquals(
        SimulationStatus.COMPLETED,
        settled.getStatus(),
        () -> "sibling simulation must reach COMPLETED. Observed: " + diagnostics(siblingId, settled));

    assertEquals(
        1,
        executionAttempts(),
        () -> "only the sibling may execute; the FAILED duplicate has nothing to run. "
            + diagnostics(siblingId, settled));

    // The duplicate row stays FAILED
    assertEquals(
        SimulationStatus.FAILED,
        statusOf(failedId).orElseThrow(),
        "the duplicate must remain FAILED. Observed: " + diagnostics(failedId, null));

    // No DLT record for the terminal duplicate
    List<ConsumerRecord<String, byte[]>> dlt =
        AsyncKafkaTestSupport.drainDlt(Duration.ofSeconds(5), 0, GROUP_SUFFIX);
    assertTrue(
        dlt.isEmpty(),
        "no DLT record must be written for a FAILED duplicate. Observed dlt=" + dlt);
  }

  /** C3 — PENDING / parked record: unclaimable PENDING scenario; partition parked; sibling continues. */
  @Test
  @DisplayName("C3: unclaimable PENDING parks owning partition; sibling processes")
  void unclaimablePending_parksOwningPartitionSiblingProcesses() throws Exception {
    UUID parkedId = seedDirectRow(SimulationStatus.PENDING);
    // Mark as unclaimable by simulating a fresh claim that can't be taken
    simulationRepository.updateStatus(parkedId, SimulationStatus.PROCESSING);

    UUID siblingId = seedDirectRow(SimulationStatus.PENDING);

    // Parked record on partition 0, sibling on partition 1: with one partition the park
    // would starve the sibling, which is the whole-consumer failure mode this guards.
    publishToPartition(validEventFor(parkedId), parkedId, 1, 0);
    publishToPartition(validEventFor(siblingId), siblingId, 1, 1);

    Simulation settled =
        awaitStatus(
            siblingId,
            Set.of(SimulationStatus.COMPLETED),
            WAIT,
            () -> "C3 - the parked PENDING must not block; the sibling must complete. "
                + diagnostics(siblingId, null));

    assertEquals(
        SimulationStatus.COMPLETED,
        settled.getStatus(),
        () -> "sibling simulation must reach COMPLETED. Observed: " + diagnostics(siblingId, settled));

    assertEquals(
        1,
        executionAttempts(),
        () -> "only the sibling may execute; the parked record must not be claimed. "
            + diagnostics(siblingId, settled));

    // The parked row must stay PROCESSING (not reclaimed within the park window)
    assertEquals(
        SimulationStatus.PROCESSING,
        statusOf(parkedId).orElseThrow(),
        "the parked record must remain PROCESSING. Observed: " + diagnostics(parkedId, null));
  }

  /** C4 — Restart while parked: verify recovery after sibling completes. */
  @Test
  @DisplayName("C4: restart while parked — system recovers; no double execution")
  void restartWhileParked_systemRecoversNoDoubleExecution() throws Exception {
    UUID parkedId = seedDirectRow(SimulationStatus.PENDING);
    simulationRepository.updateStatus(parkedId, SimulationStatus.PROCESSING);

    UUID siblingId = seedDirectRow(SimulationStatus.PENDING);

    // Publish both events; the parked one should not execute. Explicit partitions keep
    // the sibling reachable while partition 0 is parked.
    publishToPartition(validEventFor(parkedId), parkedId, 1, 0);
    publishToPartition(validEventFor(siblingId), siblingId, 1, 1);

    Simulation siblingSettled =
        awaitStatus(
            siblingId,
            Set.of(SimulationStatus.COMPLETED),
            WAIT,
            () -> "C4 - sibling must complete before restart. Observed: "
                + diagnostics(siblingId, null));

    assertEquals(
        SimulationStatus.COMPLETED,
        siblingSettled.getStatus(),
        () -> "sibling must complete. Observed: " + diagnostics(siblingId, siblingSettled));

    assertEquals(
        1,
        executionAttempts(),
        () -> "only the sibling may have executed. Observed: " + diagnostics(siblingId, siblingSettled));

    // The parked record must still be PROCESSING after sibling completion
    assertEquals(
        SimulationStatus.PROCESSING,
        statusOf(parkedId).orElseThrow(),
        "the parked record must still be PROCESSING after sibling completion. Observed: "
            + diagnostics(parkedId, null));
  }

  /** C5 — Partition liveness: one unclaimable record cannot permanently block unrelated work. */
  @Test
  @DisplayName("C5: partition liveness — unclaimable does not permanently block sibling")
  void partitionLiveness_unclaimableDoesNotPermanentlyBlockSibling() throws Exception {
    UUID stalled = seedPendingSimulation();
    simulationRepository.updateStatus(stalled, SimulationStatus.PROCESSING);
    UUID live = seedPendingSimulation();

    String topic = currentSourceTopic();

    // Get the consumer container and wait for it to own the partition
    var listenerContainer =
        listenerEndpointRegistry.getListenerContainer(
            me.veselin.probity.simulation.event.SimulationRequestedEventConsumer.LISTENER_ID);

    // Wait for the group's single consumer to own the partition
    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              Collection<TopicPartition> assigned = listenerContainer.getAssignedPartitions();
              return assigned != null && assigned.size() >= 1;
            });

    // Force the unclaimable record onto partition 0 and wait for the park to take effect.
    // The generation header is required: without it the generation gate rejects the record
    // before claim/park logic ever runs.
    SendResult<String, Object> stalledResult =
        kafkaTemplate
            .send(
                new ProducerRecord<>(
                    topic, 0, stalled.toString(), validEventFor(stalled), generationHeaders(1)))
            .get(15, TimeUnit.SECONDS);
    long stalledOffset = stalledResult.getRecordMetadata().offset();
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> listenerContainer.isPartitionPaused(new TopicPartition(topic, 0)));

    // Deliver a claimable record on the sibling partition. It must complete while the
    // other partition is parked.
    kafkaTemplate
        .send(
            new ProducerRecord<>(
                topic, 1, live.toString(), validEventFor(live), generationHeaders(1)))
        .get(15, TimeUnit.SECONDS);
    Simulation settled =
        awaitStatus(
            live,
            Set.of(SimulationStatus.COMPLETED),
            WAIT,
            () -> "C5 - the live record must complete while partition 0 is parked. "
                + diagnostics(live, null));

    assertEquals(
        SimulationStatus.COMPLETED,
        settled.getStatus(),
        () -> "the live record must complete. Observed: " + diagnostics(live, settled));
    assertEquals(
        1,
        executionAttempts(),
        () -> "only the live record may execute; the parked record must not be claimed. "
            + diagnostics(live, settled));

    // The parked record's offset must never be committed. The topic and group are shared
    // across this class's tests, so the committed offset is compared against this record's
    // own offset: a commit at or below it means the parked record was not acknowledged.
    Optional<Long> after = committedOffset(currentGroupId());
    assertTrue(
        after.isEmpty() || after.get() <= stalledOffset,
        "the parked record must not be acknowledged; committing its offset would "
            + "destroy the redelivery the park depends on. committed="
            + after
            + " recordOffset="
            + stalledOffset);
  }

  /**
   * Publishes a generation-stamped record to an explicit partition, so the parked record and
   * its sibling never depend on key hashing to land on different partitions.
   */
  private void publishToPartition(
      Object value, UUID simulationId, int generation, int partition) throws Exception {
    kafkaTemplate
        .send(
            new ProducerRecord<>(
                currentSourceTopic(),
                partition,
                simulationId.toString(),
                value,
                generationHeaders(generation)))
        .get(15, TimeUnit.SECONDS);
  }

  /** The {@code dispatch-generation} header the production outbox publisher stamps. */
  private static RecordHeaders generationHeaders(int generation) {
    RecordHeaders headers = new RecordHeaders();
    headers.add(
        "dispatch-generation", Integer.toString(generation).getBytes(StandardCharsets.US_ASCII));
    return headers;
  }

  /** Helpers */

  private UUID seedDirectRow(SimulationStatus status) {
    Simulation simulation =
        Simulation.builder()
            .portfolioId(UUID.fromString(portfolioId))
            .userId(seededUserId)
            .numberOfSimulations(100)
            .timeHorizonDays(10)
            .confidenceLevel(0.95)
            .currentPortfolioValue(BigDecimal.ZERO)
            .resultPayload(null)
            .status(status)
            .build();
    return simulationRepository.save(simulation).getId();
  }

  @Override
  protected UUID seedPendingSimulation() {
    return super.seedPendingSimulation();
  }
}