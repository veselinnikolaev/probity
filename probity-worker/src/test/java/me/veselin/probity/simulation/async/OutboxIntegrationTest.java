package me.veselin.probity.simulation.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import me.veselin.probity.common.domain.event.SimulationRequestedEvent;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;
import me.veselin.probity.simulation.service.OutboxPublisher;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration test for the Transactional Outbox implementation (M1).
 *
 * <p>Tests the correctness guarantees:
 *
 * <ol>
 *   <li>Atomic simulation/outbox commit
 *   <li>Rollback when the outbox insert fails
 *   <li>Successful publication
 *   <li>Kafka unavailable during publication
 *   <li>Recovery after publisher restart
 *   <li>Kafka ack + crash before published_at update
 *   <li>Duplicate delivery does not execute a simulation twice
 *   <li>Concurrent publishers do not corrupt/permanently lose events
 *   <li>Expired claims can be recovered
 * </ol>
 *
 * <p>Runs against a Testcontainers PostgreSQL database and the shared Testcontainers Kafka broker.
 * The listener is deliberately dormant: these tests assert on the publisher and the database, not
 * on execution of the event.
 */
public class OutboxIntegrationTest extends BaseAsyncSimulationIntegrationTest {

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    // A dormant listener plus its own topic/group: the publisher publishes, but no
    // consumer executes the event or shares a topic with another test class.
    AsyncKafkaTestSupport.registerAsyncBroker(registry, false, "m1-outbox");
  }

  private static final Logger log = LoggerFactory.getLogger(OutboxIntegrationTest.class);

  @Autowired private OutboxPublisher outboxPublisher;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private EntityManager entityManager;

  private UUID testSimulationId;

  @BeforeEach
  void setupTest(TestInfo testInfo) {
    log.info("OutboxIntegrationTest.setupTest: {}", testInfo.getDisplayName());
    testSimulationId = seedPendingSimulation();
  }

  // ── 1. Atomic simulation / outbox commit ─────────────────────────────

  @Test
  @DisplayName("1. Atomic simulation/outbox commit - both rows are committed together")
  void testAtomicSimulationOutboxCommit() {
    OutboxState state = outboxState(testSimulationId);
    assertNotNull(state, "Outbox row should be committed with the simulation");
    assertEquals(0, state.errorCount(), "Initial error count should be 0");
    assertFalse(state.published(), "A freshly seeded row must not be published");

    List<Object[]> pending = simulationRepository.lockPendingOutboxBatch(1, Instant.now());
    assertEquals(1, pending.size(), "Should have exactly 1 pending outbox record");
    assertEquals(
        testSimulationId, pending.get(0)[0], "Pending row id should match the simulation id");
    assertEquals(
        testSimulationId, pending.get(0)[1], "simulation_id should match the simulation id");
  }

  // ── 2. Rollback when outbox insert fails ───────────────────────────

  @Test
  @DisplayName("2. Rollback when outbox insert fails - the simulation insert rolls back with it")
  void testRollbackWhenOutboxInsertFails() {
    AtomicReference<UUID> rolledBackId = new AtomicReference<>();
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    assertThrows(
        RuntimeException.class,
        () ->
            transaction.executeWithoutResult(
                status -> {
                  Simulation simulation =
                      Simulation.builder()
                          .portfolioId(UUID.fromString(portfolioId))
                          .userId(seededUserId)
                          .numberOfSimulations(100)
                          .timeHorizonDays(10)
                          .confidenceLevel(0.95)
                          .currentPortfolioValue(BigDecimal.ZERO)
                          .resultPayload(null)
                          .status(SimulationStatus.PENDING)
                          .build();
                  Simulation saved = simulationRepository.save(simulation);
                  rolledBackId.set(saved.getId());
                  entityManager.flush();

                  // event_type is VARCHAR(50): this insert fails after the simulation INSERT
                  // has already been flushed, so only one transaction can explain the outcome.
                  String tooLongEventType = "X".repeat(51);
                  simulationRepository.insertOutboxEvent(
                      UUID.randomUUID(), saved.getId(), tooLongEventType, "{}");
                }));

    assertNotNull(
        rolledBackId.get(), "The simulation should have been saved before the outbox insert");
    assertTrue(
        simulationRepository.findById(rolledBackId.get()).isEmpty(),
        "The simulation insert must roll back when its outbox insert fails");
  }

  // ── 3. Successful publication ──────────────────────────────────────

  @Test
  @DisplayName(
      "3. Successful publication - event published to Kafka and outbox marked as published")
  void testSuccessfulPublication() {
    outboxPublisher.publishPendingEvents();

    OutboxState state = outboxState(testSimulationId);
    assertTrue(state.published(), "The publisher should set published_at");
    assertEquals(0, state.errorCount(), "Error count should be 0 after successful publication");
    assertNull(state.claimToken(), "The claim token should be cleared after publication");
    assertNull(state.claimedUntil(), "The lease should be cleared after publication");

    List<ConsumerRecord<String, String>> records =
        drainSource(testSimulationId.toString(), 1, Duration.ofSeconds(20));
    assertEquals(1, records.size(), "The event should have reached Kafka");
    assertEquals(
        testSimulationId.toString(),
        records.get(0).key(),
        "The Kafka key should be the simulation id");
  }

  // ── 4. Kafka unavailable during publication ─────────────────────────

  @Test
  @DisplayName("4. Kafka unavailable during publication - records remain pending for retry")
  void testKafkaUnavailableDuringPublication() throws Exception {
    OutboxPublisher failingPublisher = Mockito.spy(outboxPublisher);
    Mockito.doThrow(new org.apache.kafka.common.KafkaException("simulated broker outage"))
        .when(failingPublisher)
        .sendToKafka(any(UUID.class), any(SimulationRequestedEvent.class));

    failingPublisher.publishPendingEvents();

    OutboxState state = outboxState(testSimulationId);
    assertFalse(state.published(), "A failed publication must leave published_at NULL");
    assertEquals(1, state.errorCount(), "A failed publication must increment error_count");
    assertNull(state.claimToken(), "The claim token must be cleared so the row can be retried");
    assertNotNull(state.claimedUntil(), "A failure must push the retry lease forward");
    assertTrue(
        state.claimedUntil().isAfter(Instant.now()),
        "The retry lease must be in the future (exponential backoff)");

    assertTrue(
        simulationRepository.lockPendingOutboxBatch(1, Instant.now()).isEmpty(),
        "The backoff lease must hold the failed row out of the current poll");
    assertEquals(
        1,
        simulationRepository.lockPendingOutboxBatch(1, Instant.now().plusSeconds(10)).size(),
        "The row must become claimable again after the backoff");
  }

  // ── 5. Recovery after publisher restart ────────────────────────────

  @Test
  @DisplayName("5. Recovery after publisher restart - outbox row survives and is re-claimable")
  void testRecoveryAfterPublisherRestart() {
    UUID firstClaimToken = UUID.randomUUID();
    assertEquals(
        1,
        simulationRepository.claimOutboxRecords(
            List.of(testSimulationId), firstClaimToken, Instant.now().plusSeconds(3600)),
        "The row should be claimable by the first publisher");
    assertTrue(
        simulationRepository.lockPendingOutboxBatch(1, Instant.now()).isEmpty(),
        "A live lease must hide the row from other publishers");

    // Simulate a publisher that died before publishing: its lease eventually expires.
    jdbcTemplate.update(
        "UPDATE simulation_outbox SET claimed_until = now() - interval '1 second' WHERE id = ?",
        testSimulationId);

    assertEquals(
        1,
        simulationRepository.lockPendingOutboxBatch(1, Instant.now()).size(),
        "The row must survive the dead publisher and become claimable again");
    assertFalse(
        outboxState(testSimulationId).published(),
        "The row must still be unpublished after the restart");

    outboxPublisher.publishPendingEvents();
    assertTrue(
        outboxState(testSimulationId).published(),
        "A restarted publisher must be able to publish the surviving row");
  }

  // ── 6. Kafka ack + crash before published_at update ──────────────────

  @Test
  @DisplayName("6. Kafka ack + crash before published_at update - record may be re-published")
  void testKafkaAckBeforePublishedAtUpdate() throws Exception {
    // First delivery: a publisher leases the row and the broker acknowledges the send,
    // then the publisher "crashes" before markOutboxPublished, so the row is still
    // unpublished but leased to the dead publisher.
    assertEquals(
        1,
        simulationRepository.claimOutboxRecords(
            List.of(testSimulationId), UUID.randomUUID(), Instant.now().plusSeconds(300)),
        "The row should be leased exactly as a live publisher leases it");
    publishWithGeneration(validEventFor(testSimulationId), testSimulationId);
    assertFalse(
        outboxState(testSimulationId).published(),
        "The outbox row must still be unpublished after a crash before the mark");

    // The dead publisher's lease expires and the row is re-published, producing a
    // second Kafka record for the same key: delivery is at-least-once.
    jdbcTemplate.update(
        "UPDATE simulation_outbox SET claimed_until = now() - interval '1 second' WHERE id = ?",
        testSimulationId);

    outboxPublisher.publishPendingEvents();
    assertTrue(
        outboxState(testSimulationId).published(),
        "The recovered publisher must mark the row published");

    List<ConsumerRecord<String, String>> records =
        drainSource(testSimulationId.toString(), 2, Duration.ofSeconds(20));
    assertTrue(
        records.size() >= 2, "The same event must be deliverable twice when the mark is lost");
  }

  // ── 7. Duplicate delivery does not execute simulation twice ─────────

  @Test
  @DisplayName(
      "7. Duplicate delivery does not execute a simulation twice - idempotent consumer guard via fence tokens")
  void testDuplicateDeliveryIdempotentGuard() {
    // The consumer's claim is fenced on updated_at. A second delivery while the first
    // claim is live matches no row, so the simulation is not executed twice.
    Optional<Instant> firstClaim =
        simulationRepository.claimForProcessing(testSimulationId, Duration.ofSeconds(300));

    assertTrue(firstClaim.isPresent(), "First claim should succeed");

    Optional<Instant> secondClaim =
        simulationRepository.claimForProcessing(testSimulationId, Duration.ofSeconds(300));

    assertTrue(
        secondClaim.isEmpty(),
        "Second claim on the same row should be rejected while its lease is live");
  }

  // ── 8. Concurrent publishers do not corrupt/permanently lose events ─────

  @Test
  @DisplayName(
      "8. Concurrent publishers do not corrupt/permanently lose events - FOR UPDATE SKIP LOCKED enables safe concurrent claiming")
  void testConcurrentPublishersSafety() throws Exception {
    UUID secondSimulationId = seedPendingSimulation();
    Set<UUID> seededIds = Set.of(testSimulationId, secondSimulationId);

    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch firstLocked = new CountDownLatch(1);
    CountDownLatch secondAttempted = new CountDownLatch(1);

    try {
      // Publisher A locks one row and holds its transaction open. Publisher B must
      // skip the locked row and take the other one rather than blocking or claiming
      // the same work.
      Future<List<Object[]>> first =
          pool.submit(
              () ->
                  transaction.execute(
                      status -> {
                        List<Object[]> rows =
                            simulationRepository.lockPendingOutboxBatch(1, Instant.now());
                        firstLocked.countDown();
                        try {
                          secondAttempted.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                        return rows;
                      }));

      assertTrue(firstLocked.await(10, TimeUnit.SECONDS), "The first publisher should lock a row");

      Future<List<Object[]>> second =
          pool.submit(
              () ->
                  transaction.execute(
                      status -> {
                        try {
                          return simulationRepository.lockPendingOutboxBatch(1, Instant.now());
                        } finally {
                          secondAttempted.countDown();
                        }
                      }));

      List<Object[]> firstRows = first.get(15, TimeUnit.SECONDS);
      List<Object[]> secondRows = second.get(15, TimeUnit.SECONDS);

      assertNotNull(firstRows, "The first publisher should return a row");
      assertNotNull(secondRows, "The second publisher should return a row");
      assertEquals(1, firstRows.size(), "The first publisher should claim exactly one row");
      assertEquals(1, secondRows.size(), "The second publisher should claim exactly one row");

      UUID firstId = (UUID) firstRows.get(0)[0];
      UUID secondId = (UUID) secondRows.get(0)[0];
      assertEquals(
          seededIds,
          Set.of(firstId, secondId),
          "The two publishers must claim different seeded rows, never the same one");
    } finally {
      pool.shutdownNow();
    }
  }

  // ── 9. Expired claims can be recovered ─────────────────────────────

  @Test
  @DisplayName("9. Expired claims can be recovered - a stale lease is reclaimable and fenced")
  void testExpiredClaimRecovery() {
    UUID recoverySimulationId = seedPendingSimulation();

    Optional<Instant> firstClaim =
        simulationRepository.claimForProcessing(recoverySimulationId, Duration.ofSeconds(300));
    assertTrue(firstClaim.isPresent(), "The PENDING row should be claimable");
    Instant staleToken = firstClaim.get();

    assertTrue(
        simulationRepository
            .claimForProcessing(recoverySimulationId, Duration.ofSeconds(300))
            .isEmpty(),
        "A live lease must reject a second claim");

    // Simulate a worker that stopped refreshing its lease.
    jdbcTemplate.update(
        "UPDATE simulations SET updated_at = now() - interval '10 minutes' WHERE id = ?",
        recoverySimulationId);

    Optional<Instant> reclaimed =
        simulationRepository.claimForProcessing(recoverySimulationId, Duration.ofSeconds(300));
    assertTrue(reclaimed.isPresent(), "An expired lease must be reclaimable by a peer");
    Instant newToken = reclaimed.get();

    assertFalse(
        simulationRepository.releaseToPending(recoverySimulationId, staleToken),
        "The stale worker's release must be fenced out");
    assertTrue(
        simulationRepository.releaseToPending(recoverySimulationId, newToken),
        "The current owner must be able to release the claim");
    assertEquals(
        SimulationStatus.PENDING,
        simulationRepository.findById(recoverySimulationId).orElseThrow().getStatus(),
        "The row must return to PENDING after a fenced release");
  }

  // ── helpers ──────────────────────────────────────────────────────────

  /** The persisted outbox row's publication state. */
  private record OutboxState(
      boolean published, int errorCount, UUID claimToken, Instant claimedUntil) {}

  private OutboxState outboxState(UUID id) {
    return jdbcTemplate.queryForObject(
        """
                        SELECT published_at IS NOT NULL AS published,
                               error_count,
                               claim_token,
                               claimed_until
                          FROM simulation_outbox
                         WHERE id = ?
                        """,
        (rs, rowNum) ->
            new OutboxState(
                rs.getBoolean("published"),
                rs.getInt("error_count"),
                rs.getObject("claim_token", UUID.class),
                rs.getTimestamp("claimed_until") == null
                    ? null
                    : rs.getTimestamp("claimed_until").toInstant()),
        id);
  }

  /**
   * Drains the source topic and returns records for {@code key}.
   *
   * <p>A throwaway consumer group with {@code earliest} makes the probe independent of the listener
   * and of records left by earlier tests; the key filter keeps a test from observing another test's
   * events.
   */
  private List<ConsumerRecord<String, String>> drainSource(
      String key, int minRecords, Duration timeout) {
    Properties props = new Properties();
    props.put(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, AsyncKafkaTestSupport.KAFKA.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "probity-outbox-probe-" + UUID.randomUUID());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

    List<ConsumerRecord<String, String>> collected = new ArrayList<>();
    try (Consumer<String, String> probe = new KafkaConsumer<>(props)) {
      probe.subscribe(List.of(currentSourceTopic()));
      long deadline = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadline && collected.size() < minRecords) {
        ConsumerRecords<String, String> polled = probe.poll(Duration.ofMillis(500));
        polled.forEach(
            record -> {
              if (key.equals(record.key())) {
                collected.add(record);
              }
            });
      }
    }
    return collected;
  }
}
