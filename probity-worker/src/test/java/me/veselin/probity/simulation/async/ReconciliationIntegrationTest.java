package me.veselin.probity.simulation.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;
import me.veselin.probity.simulation.reconciliation.ReconciliationHealthIndicator;
import me.veselin.probity.simulation.reconciliation.ReconciliationMetrics;
import me.veselin.probity.simulation.reconciliation.ReconciliationProperties;
import me.veselin.probity.simulation.reconciliation.ReconciliationSweeper;
import me.veselin.probity.simulation.service.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integration test matrix T1–T16 for M2 Reconciliation Sweeper.
 *
 * <p>Tests reconciliation correctness, invariants, and edge cases:
 *
 * <ul>
 *   <li>T1: Stuck PENDING recovery
 *   <li>T2: PENDING with unpublished outbox is not swept
 *   <li>T3: Live publisher claim is respected
 *   <li>T4: Stuck PROCESSING recovery
 *   <li>T5: Fresh PROCESSING untouched
 *   <li>T6: Stale PROCESSING with no outbox (alert only)
 *   <li>T7: PENDING with no outbox (alert only)
 *   <li>T8: Concurrent sweepers safety
 *   <li>T9: Crash during recovery / transaction rollback
 *   <li>T10: Duplicate Kafka delivery idempotent guard
 *   <li>T11: Completed simulation never re-executed
 *   <li>T12: Outbox retry exhaustion (M1 interaction)
 *   <li>T13: Soft-deleted rows ignored
 *   <li>T14: Terminal simulation with unpublished outbox
 *   <li>T15: Scheduler watchdog / health resilience
 *   <li>T16: Index plan and sweeper architectural invariant check
 * </ul>
 */
public class ReconciliationIntegrationTest extends BaseAsyncSimulationIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationIntegrationTest.class);

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    // Active consumer listener on a dedicated topic/group for M2 reconciliation
    AsyncKafkaTestSupport.registerAsyncBroker(registry, true, "m2-recon");
  }

  @Autowired private ReconciliationSweeper sweeper;

  @Autowired private ReconciliationProperties reconciliationProperties;

  @Autowired private ReconciliationMetrics reconciliationMetrics;

  @Autowired private ReconciliationHealthIndicator healthIndicator;

  @Autowired private OutboxPublisher outboxPublisher;

  private UUID testSimulationId;

  @BeforeEach
  void setupTest(TestInfo testInfo) {
    log.info("ReconciliationIntegrationTest.setupTest: {}", testInfo.getDisplayName());
    testSimulationId = seedPendingSimulation();
  }

  private OutboxRecord getOutbox(UUID simulationId) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT id, published_at, claim_token, claimed_until, error_count, reconcile_count, last_reconciled_at "
                + "FROM simulation_outbox WHERE simulation_id = ?",
            simulationId);
    if (rows.isEmpty()) {
      return null;
    }
    Map<String, Object> r = rows.get(0);
    return new OutboxRecord(
        (UUID) r.get("id"),
        r.get("published_at") != null,
        (UUID) r.get("claim_token"),
        r.get("claimed_until") != null
            ? ((java.sql.Timestamp) r.get("claimed_until")).toInstant()
            : null,
        ((Number) r.get("error_count")).intValue(),
        ((Number) r.get("reconcile_count")).intValue(),
        r.get("last_reconciled_at") != null
            ? ((java.sql.Timestamp) r.get("last_reconciled_at")).toInstant()
            : null);
  }

  private record OutboxRecord(
      UUID id,
      boolean published,
      UUID claimToken,
      Instant claimedUntil,
      int errorCount,
      int reconcileCount,
      Instant lastReconciledAt) {}

  // ── T1: Stuck PENDING recovery ───────────────────────────────────────────

  @Test
  @DisplayName("T1: Stuck PENDING simulation with published outbox is reset and completed")
  void testT1_StuckPendingRecovery() {
    // Setup: Simulation PENDING since 30m ago; outbox marked published 20m ago with no delivery
    jdbcTemplate.update(
        "UPDATE simulations SET created_at = now() - interval '30 minutes', updated_at = null WHERE id = ?",
        testSimulationId);
    jdbcTemplate.update(
        "UPDATE simulation_outbox SET published_at = now() - interval '20 minutes', created_at = now() - interval '30 minutes' WHERE simulation_id = ?",
        testSimulationId);

    OutboxRecord initialOutbox = getOutbox(testSimulationId);
    assertNotNull(initialOutbox);
    assertTrue(initialOutbox.published(), "Outbox starts marked published");
    assertEquals(0, initialOutbox.reconcileCount());

    // Run sweeper
    int actions = sweeper.sweepOnce();
    assertEquals(1, actions, "Sweeper should apply exactly 1 action for stuck PENDING row");

    OutboxRecord sweptOutbox = getOutbox(testSimulationId);
    assertNotNull(sweptOutbox);
    assertFalse(sweptOutbox.published(), "Outbox published_at must be reset to NULL");
    assertEquals(1, sweptOutbox.reconcileCount(), "Reconcile count must increment to 1");
    assertNotNull(sweptOutbox.lastReconciledAt(), "last_reconciled_at must be set");

    // Publisher picks it up and consumer completes it
    outboxPublisher.publishPendingEvents();

    Simulation completed =
        awaitStatus(testSimulationId, Set.of(SimulationStatus.COMPLETED), Duration.ofSeconds(20));
    assertEquals(SimulationStatus.COMPLETED, completed.getStatus());
    assertEquals(1, executionAttempts(), "Simulation must execute exactly once");
  }

  // ── T2: PENDING with unpublished outbox is not swept ─────────────────────

  @Test
  @DisplayName(
      "T2: PENDING simulation with unpublished outbox is ignored by sweeper and handled by OutboxPublisher")
  void testT2_PendingWithUnpublishedOutboxNotSwept() {
    // Setup: Outbox is unpublished, simulation created 30m ago
    jdbcTemplate.update(
        "UPDATE simulations SET created_at = now() - interval '30 minutes' WHERE id = ?",
        testSimulationId);
    jdbcTemplate.update(
        "UPDATE simulation_outbox SET published_at = null, created_at = now() - interval '30 minutes' WHERE simulation_id = ?",
        testSimulationId);

    int actions = sweeper.sweepOnce();
    assertEquals(
        0,
        actions,
        "Sweeper must not sweep unpublished outbox rows (handled by M1 OutboxPublisher)");

    OutboxRecord outbox = getOutbox(testSimulationId);
    assertEquals(0, outbox.reconcileCount(), "Reconcile count must remain 0");

    // OutboxPublisher handles it
    outboxPublisher.publishPendingEvents();
    Simulation completed =
        awaitStatus(testSimulationId, Set.of(SimulationStatus.COMPLETED), Duration.ofSeconds(20));
    assertEquals(SimulationStatus.COMPLETED, completed.getStatus());
  }

  // ── T3: Live publisher claim is respected ────────────────────────────────

  @Test
  @DisplayName("T3: Live publisher claim on outbox row is respected and skipped by sweeper")
  void testT3_LivePublisherClaimRespected() {
    // Setup: Outbox published, but active lease held until 5 minutes in future
    jdbcTemplate.update(
        "UPDATE simulations SET created_at = now() - interval '30 minutes' WHERE id = ?",
        testSimulationId);
    jdbcTemplate.update(
        "UPDATE simulation_outbox SET published_at = now() - interval '20 minutes', claimed_until = now() + interval '5 minutes', claim_token = ? WHERE simulation_id = ?",
        UUID.randomUUID(),
        testSimulationId);

    int actions = sweeper.sweepOnce();
    assertEquals(0, actions, "Sweeper must skip outbox records with active publisher lease");

    OutboxRecord outbox = getOutbox(testSimulationId);
    assertTrue(outbox.published(), "Outbox published status must remain unchanged");
    assertEquals(0, outbox.reconcileCount(), "Reconcile count must remain 0");
  }

  // ── T4: Stuck PROCESSING recovery ────────────────────────────────────────

  @Test
  @DisplayName("T4: Stuck PROCESSING simulation is reset to PENDING and completed exactly once")
  void testT4_StuckProcessingRecovery() {
    // Setup: Simulation in PROCESSING since 40m ago, outbox marked published 35m ago
    jdbcTemplate.update(
        "UPDATE simulations SET status = 'PROCESSING', updated_at = now() - interval '40 minutes' WHERE id = ?",
        testSimulationId);
    jdbcTemplate.update(
        "UPDATE simulation_outbox SET published_at = now() - interval '35 minutes' WHERE simulation_id = ?",
        testSimulationId);

    int actions = sweeper.sweepOnce();
    assertEquals(1, actions, "Sweeper must repair stuck PROCESSING simulation");

    Simulation resetSim = simulationRepository.findById(testSimulationId).orElseThrow();
    assertEquals(
        SimulationStatus.PENDING, resetSim.getStatus(), "Simulation must be reset to PENDING");

    OutboxRecord sweptOutbox = getOutbox(testSimulationId);
    assertFalse(sweptOutbox.published(), "Outbox must be reset to unpublished");
    assertEquals(1, sweptOutbox.reconcileCount());

    // OutboxPublisher re-publishes and consumer processes to completion
    outboxPublisher.publishPendingEvents();
    Simulation completed =
        awaitStatus(testSimulationId, Set.of(SimulationStatus.COMPLETED), Duration.ofSeconds(20));
    assertEquals(SimulationStatus.COMPLETED, completed.getStatus());
    assertEquals(1, executionAttempts(), "Must execute to completion exactly once");
  }

  // ── T5: Fresh PROCESSING untouched ───────────────────────────────────────

  @Test
  @DisplayName("T5: Fresh PROCESSING simulation within grace window is untouched")
  void testT5_FreshProcessingUntouched() {
    // Setup: Simulation in PROCESSING updated recently (now)
    jdbcTemplate.update(
        "UPDATE simulations SET status = 'PROCESSING', updated_at = now() WHERE id = ?",
        testSimulationId);
    jdbcTemplate.update(
        "UPDATE simulation_outbox SET published_at = now() WHERE simulation_id = ?",
        testSimulationId);

    int actions = sweeper.sweepOnce();
    assertEquals(0, actions, "Fresh PROCESSING simulations must not be touched");

    Simulation sim = simulationRepository.findById(testSimulationId).orElseThrow();
    assertEquals(SimulationStatus.PROCESSING, sim.getStatus());
  }

  // ── T6: Stale PROCESSING with no outbox (alert only) ──────────────────────

  @Test
  @DisplayName(
      "T6: Stale PROCESSING simulation with no outbox is alert-only and never mutated to PENDING")
  void testT6_StaleProcessingWithNoOutbox_AlertOnly() {
    // Setup: Delete outbox row and leave simulation in PROCESSING state older than grace
    jdbcTemplate.update("DELETE FROM simulation_outbox WHERE simulation_id = ?", testSimulationId);
    jdbcTemplate.update(
        "UPDATE simulations SET status = 'PROCESSING', updated_at = now() - interval '40 minutes' WHERE id = ?",
        testSimulationId);

    int actions = sweeper.sweepOnce();
    assertEquals(
        0, actions, "Sweeper must never mutate stale PROCESSING row without an outbox row");

    Simulation sim = simulationRepository.findById(testSimulationId).orElseThrow();
    assertEquals(
        SimulationStatus.PROCESSING,
        sim.getStatus(),
        "Status must remain PROCESSING to prevent silent stranding");
  }

  // ── T7: PENDING with no outbox (alert only) ───────────────────────────────

  @Test
  @DisplayName(
      "T7: PENDING simulation with no outbox is alert-only and never creates fabricated events")
  void testT7_PendingWithNoOutbox_AlertOnly() {
    // Setup: Delete outbox row, leave simulation in PENDING created 30m ago
    jdbcTemplate.update("DELETE FROM simulation_outbox WHERE simulation_id = ?", testSimulationId);
    jdbcTemplate.update(
        "UPDATE simulations SET created_at = now() - interval '30 minutes' WHERE id = ?",
        testSimulationId);

    int actions = sweeper.sweepOnce();
    assertEquals(
        0,
        actions,
        "Sweeper must not mutate or fabricate outbox events for PENDING without outbox");

    Simulation sim = simulationRepository.findById(testSimulationId).orElseThrow();
    assertEquals(SimulationStatus.PENDING, sim.getStatus());
    assertNull(getOutbox(testSimulationId), "No outbox row must be fabricated");
  }

  // ── T8: Concurrent sweepers safety ───────────────────────────────────────

  @Test
  @DisplayName(
      "T8: Concurrent sweepers on the same candidate apply exactly one action (SKIP LOCKED)")
  void testT8_ConcurrentSweepersSafety() throws Exception {
    // Setup: Candidate eligible for Q1 reset
    jdbcTemplate.update(
        "UPDATE simulations SET created_at = now() - interval '30 minutes', updated_at = null WHERE id = ?",
        testSimulationId);
    jdbcTemplate.update(
        "UPDATE simulation_outbox SET published_at = now() - interval '20 minutes', created_at = now() - interval '30 minutes' WHERE simulation_id = ?",
        testSimulationId);

    int threads = 2;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicInteger totalActions = new AtomicInteger(0);

    List<Future<?>> futures = new java.util.ArrayList<>();
    for (int i = 0; i < threads; i++) {
      futures.add(
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  int applied = sweeper.sweepOnce();
                  totalActions.addAndGet(applied);
                } catch (Exception e) {
                  log.error("Concurrent sweep error", e);
                }
              }));
    }

    startLatch.countDown();
    for (Future<?> f : futures) {
      f.get(10, TimeUnit.SECONDS);
    }
    executor.shutdown();

    assertEquals(
        1,
        totalActions.get(),
        "Exactly one sweep action must be applied across concurrent instances");
    OutboxRecord outbox = getOutbox(testSimulationId);
    assertEquals(1, outbox.reconcileCount(), "Reconcile count must be exactly 1");
  }

  // ── T9: Crash during recovery / transaction rollback ─────────────────────

  @Test
  @DisplayName("T9: Rolled back recovery transaction leaves outbox published for next sweep cycle")
  void testT9_CrashDuringRecovery_Rollback() {
    // Setup: Candidate eligible for Q2 reset
    jdbcTemplate.update(
        "UPDATE simulations SET status = 'PROCESSING', updated_at = now() - interval '40 minutes' WHERE id = ?",
        testSimulationId);
    jdbcTemplate.update(
        "UPDATE simulation_outbox SET published_at = now() - interval '35 minutes' WHERE simulation_id = ?",
        testSimulationId);

    Instant oldStateSince = Instant.now().minus(Duration.ofMinutes(40));

    // Attempt reset with mismatched observed timestamp (simulating concurrency / state movement)
    boolean resetResult =
        sweeper.resetProcessing(
            new me.veselin.probity.simulation.persistence.SimulationRepository
                .ReconciliationCandidate(
                testSimulationId,
                Instant.now().minus(Duration.ofMinutes(100)),
                UUID.randomUUID(),
                Instant.now(),
                null));

    assertFalse(
        resetResult, "Reset must fail and roll back when observed timestamp does not match");

    // Verify outbox remains published after rollback
    OutboxRecord outboxAfterRollback = getOutbox(testSimulationId);
    assertTrue(
        outboxAfterRollback.published(),
        "Outbox must remain published after failed/rolled-back reset");

    // Next legitimate sweep cycle succeeds
    int actions = sweeper.sweepOnce();
    assertEquals(1, actions, "Subsequent legitimate sweep cycle must succeed");

    Simulation sim = simulationRepository.findById(testSimulationId).orElseThrow();
    assertEquals(SimulationStatus.PENDING, sim.getStatus());
  }

  // ── T10: Duplicate Kafka delivery idempotent guard ───────────────────────

  @Test
  @DisplayName("T10: Duplicate Kafka delivery executes simulation only once")
  void testT10_DuplicateKafkaDelivery_Idempotent() throws Exception {
    // Publish event to Kafka twice
    publishWithGeneration(validEventFor(testSimulationId), testSimulationId);
    publishWithGeneration(validEventFor(testSimulationId), testSimulationId);

    Simulation completed =
        awaitStatus(testSimulationId, Set.of(SimulationStatus.COMPLETED), Duration.ofSeconds(20));
    assertEquals(SimulationStatus.COMPLETED, completed.getStatus());
    assertEquals(1, executionAttempts(), "Duplicate deliveries must execute only once");
  }

  // ── T11: Completed simulation never re-executed ──────────────────────────

  @Test
  @DisplayName(
      "T11: Completed simulation is ignored by sweeper and duplicate deliveries are acknowledged")
  void testT11_CompletedNeverReExecuted() throws Exception {
    // Seed and complete simulation
    jdbcTemplate.update(
        "UPDATE simulations SET status = 'COMPLETED', updated_at = now() - interval '40 minutes' WHERE id = ?",
        testSimulationId);
    jdbcTemplate.update(
        "UPDATE simulation_outbox SET published_at = null, created_at = now() - interval '40 minutes' WHERE simulation_id = ?",
        testSimulationId);

    int actions = sweeper.sweepOnce();
    assertEquals(0, actions, "Sweeper must ignore COMPLETED simulations");

    // Producer publishes and consumer receives duplicate delivery
    outboxPublisher.publishPendingEvents();
    Thread.sleep(1500);

    Simulation sim = simulationRepository.findById(testSimulationId).orElseThrow();
    assertEquals(SimulationStatus.COMPLETED, sim.getStatus());
    assertEquals(0, executionAttempts(), "Completed simulation must not execute again");
  }

  // ── T12: Outbox retry exhaustion (M1 interaction) ────────────────────────

  @Test
  @DisplayName("T12: Outbox error_count and backoff are not cleared or corrupted by sweeper")
  void testT12_OutboxRetryExhaustion_M1Interaction() {
    jdbcTemplate.update(
        "UPDATE simulations SET created_at = now() - interval '30 minutes' WHERE id = ?",
        testSimulationId);
    jdbcTemplate.update(
        "UPDATE simulation_outbox SET error_count = 5, published_at = null, claimed_until = now() - interval '1 minute' WHERE simulation_id = ?",
        testSimulationId);

    int actions = sweeper.sweepOnce();
    assertEquals(0, actions, "Sweeper must not mutate unpublished failing outbox records");

    OutboxRecord outbox = getOutbox(testSimulationId);
    assertEquals(5, outbox.errorCount(), "Error count must remain intact");
  }

  // ── T13: Soft-deleted rows ignored ───────────────────────────────────────

  @Test
  @DisplayName("T13: Soft-deleted simulations are ignored by sweeper")
  void testT13_SoftDeletedRowsIgnored() {
    jdbcTemplate.update(
        "UPDATE simulations SET deleted = true, deleted_at = now(), created_at = now() - interval '30 minutes' WHERE id = ?",
        testSimulationId);
    jdbcTemplate.update(
        "UPDATE simulation_outbox SET published_at = now() - interval '20 minutes' WHERE simulation_id = ?",
        testSimulationId);

    int actions = sweeper.sweepOnce();
    assertEquals(0, actions, "Sweeper must ignore soft-deleted simulations");

    OutboxRecord outbox = getOutbox(testSimulationId);
    assertTrue(outbox.published(), "Outbox published state must remain untouched");
  }

  // ── T14: Terminal simulation with unpublished outbox ─────────────────────

  @Test
  @DisplayName("T14: Terminal FAILED simulation with unpublished outbox is ignored by sweeper")
  void testT14_TerminalWithUnpublishedOutboxIgnored() {
    jdbcTemplate.update(
        "UPDATE simulations SET status = 'FAILED', created_at = now() - interval '30 minutes' WHERE id = ?",
        testSimulationId);
    jdbcTemplate.update(
        "UPDATE simulation_outbox SET published_at = null WHERE simulation_id = ?",
        testSimulationId);

    int actions = sweeper.sweepOnce();
    assertEquals(0, actions, "Sweeper must not touch FAILED terminal simulations");

    Simulation sim = simulationRepository.findById(testSimulationId).orElseThrow();
    assertEquals(SimulationStatus.FAILED, sim.getStatus());
  }

  // ── T15: Scheduler watchdog and health indicator ─────────────────────────

  @Test
  @DisplayName("T15: ReconciliationHealthIndicator reports valid state and duration stats")
  void testT15_SchedulerWatchdog_HealthResilience() {
    int actions = sweeper.sweepOnce();

    Health health = healthIndicator.health();
    assertNotNull(health);
    assertNotEquals(
        Status.OUT_OF_SERVICE,
        health.getStatus(),
        "Health must not be OUT_OF_SERVICE on normal run");

    Map<String, Object> details = health.getDetails();
    assertNotNull(details.get("lastSuccessfulCycleAt"));
    assertNotNull(details.get("lastCycleDurationMs"));
  }

  // ── T16: Invariant verification and index scan ───────────────────────────

  @Test
  @DisplayName("T16: Sweeper architectural invariants and partial index presence verified")
  void testT16_SweeperInvariantsAndIndexPresence() throws Exception {
    // Invariant check: ReconciliationSweeper must NOT hold a KafkaTemplate or
    // SimulationApplicationService/MonteCarloSimulationService
    for (Field f : ReconciliationSweeper.class.getDeclaredFields()) {
      assertFalse(
          KafkaTemplate.class.isAssignableFrom(f.getType()),
          "ReconciliationSweeper must never have a KafkaTemplate field (no direct Kafka publish)");
      assertFalse(
          f.getType().getSimpleName().contains("MonteCarlo"),
          "ReconciliationSweeper must never have a simulation execution engine field");
    }

    // Verify index existence and query execution
    List<Map<String, Object>> indexRows =
        jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = 'simulations' AND indexname = 'idx_simulations_status_state_age'");
    assertEquals(
        1,
        indexRows.size(),
        "Partial index idx_simulations_status_state_age must exist on simulations table");

    // Verify query executes with no error
    var pending = simulationRepository.findPendingSimulationsWithoutDelivery(900, 1800, 10);
    assertNotNull(pending);
  }
}
