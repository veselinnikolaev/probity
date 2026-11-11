package me.veselin.probity.simulation.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;
import me.veselin.probity.simulation.persistence.SimulationRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 2 (root cause B) — {@code SimulationRepository.findLiveState} must map the projection
 * without a caller-side cast.
 *
 * <p>Regression this pins: the JPQL query selected two columns but the repository method returned
 * {@code Optional<Object[]>}, and the adapter cast element zero to {@code String}. That cast is not
 * checked against the JPQL selection at compile time, so a change in how Spring Data materialises
 * the row surfaced as a {@link ClassCastException} inside the listener. The exception was raised
 * <em>after</em> the claim was refused, so it could not be classified as an attempt failure: the
 * recoverer rejects it ("failure reached recovery without an attempt fence"), recovery fails, and
 * the single-partition consumer never advances past the record.
 *
 * <p>Two layers of evidence:
 *
 * <ol>
 *   <li>Direct repository assertions for every status the consumer dispatches on, plus the two
 *       "absent" cases (soft-deleted, unknown) that mean "ack and skip".
 *   <li>A listener-level assertion that an unclaimable terminal duplicate no longer wedges the
 *       partition: the duplicate is followed by a valid record, and only the valid one executes.
 *       With the cast defect the duplicate's recovery fails and the follower never runs.
 * </ol>
 *
 * <p>The projection is deliberately not exercised through mocks: the defect lived in the mapping
 * from the real PostgreSQL tuple, so only a real database proves it.
 */
class SimulationLiveStateProjectionIntegrationTest extends BaseAsyncSimulationIntegrationTest {

  private static final Duration RUNNABLE_WAIT = Duration.ofSeconds(20);

  private static final String GROUP_SUFFIX = "b-live-state";

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    AsyncKafkaTestSupport.registerAsyncBroker(registry, true, GROUP_SUFFIX);
  }

  @Test
  @DisplayName("B: a live PENDING row projects its status and dispatch generation")
  void livePendingRow_projectsStatusAndGeneration() {
    UUID id = seedDirectRow(SimulationStatus.PENDING);
    jdbcTemplate.update("UPDATE simulations SET dispatch_generation = 2 WHERE id = ?", id);

    SimulationRepository.LiveState state =
        simulationRepository
            .findLiveState(id)
            .orElseThrow(() -> new AssertionError("expected a live state for " + id));

    assertEquals(SimulationStatus.PENDING, state.status(), "the PENDING status column must map");
    assertEquals(2, state.dispatchGeneration(), "the dispatch_generation column must map");
  }

  @Test
  @DisplayName("B: every terminal and in-flight status the consumer dispatches on is projected")
  void everyDispatchableStatus_isProjected() {
    UUID id = seedDirectRow(SimulationStatus.PENDING);

    for (SimulationStatus status :
        Set.of(
            SimulationStatus.PROCESSING,
            SimulationStatus.COMPLETED,
            SimulationStatus.FAILED,
            SimulationStatus.PENDING)) {
      jdbcTemplate.update("UPDATE simulations SET status = ? WHERE id = ?", status.name(), id);

      SimulationRepository.LiveState state =
          simulationRepository
              .findLiveState(id)
              .orElseThrow(
                  () -> new AssertionError("expected a live state for status " + status));

      assertEquals(status, state.status(), () -> "status " + status + " must round-trip");
    }
  }

  @Test
  @DisplayName("B: a soft-deleted row is absent, so the consumer acknowledges and skips it")
  void softDeletedRow_isAbsent() {
    UUID id = seedDirectRow(SimulationStatus.PENDING);
    jdbcTemplate.update(
        "UPDATE simulations SET deleted = true, deleted_at = now() WHERE id = ?", id);

    assertTrue(
        simulationRepository.findLiveState(id).isEmpty(),
        "a soft-deleted row must not read as live; otherwise it would read as runnable PENDING");
  }

  @Test
  @DisplayName("B: an unknown id is absent")
  void unknownId_isAbsent() {
    assertTrue(
        simulationRepository.findLiveState(UUID.randomUUID()).isEmpty(),
        "an id with no row must project as absent, not throw");
  }

  @Test
  @DisplayName(
      "B: an unclaimable COMPLETED duplicate is acknowledged and does not wedge its partition")
  void unclaimableTerminalDuplicate_doesNotWedgeThePartition() throws Exception {
    // Both rows are saved directly (no outbox row) so the scheduled publisher cannot race the
    // status setup and execute either row behind the test's back.
    UUID duplicateId = seedDirectRow(SimulationStatus.COMPLETED);
    UUID runnableId = seedDirectRow(SimulationStatus.PENDING);

    publishWithGeneration(validEventFor(duplicateId), duplicateId, 1);
    publishWithGeneration(validEventFor(runnableId), runnableId, 1);

    Simulation runnable =
        awaitStatus(
            runnableId,
            Set.of(SimulationStatus.COMPLETED),
            RUNNABLE_WAIT,
            () ->
                "the duplicate on "
                    + duplicateId
                    + " must be acknowledged, not retried forever, or the follower on the single "
                    + "partition never runs.");

    assertEquals(SimulationStatus.COMPLETED, runnable.getStatus());
    assertEquals(
        1,
        executionAttempts(),
        "only the valid record may execute; the terminal duplicate has nothing to run. Observed: "
            + diagnostics(runnableId, runnable));
    assertEquals(
        Optional.of(SimulationStatus.COMPLETED),
        statusOf(duplicateId),
        "the duplicate must not mutate the terminal row. Observed: "
            + diagnostics(duplicateId, null));
  }

  /**
   * Saves a row directly, bypassing {@link #seedPendingSimulation()}'s outbox insert.
   *
   * <p>Several assertions in this class depend on a row's status being exactly what the test set,
   * so nothing may publish it in the background.
   */
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

  /** Publishes with the generation header the production outbox carries. */
  protected void publishWithGeneration(Object value, UUID simulationId, int generation)
      throws Exception {
    RecordHeaders headers = new RecordHeaders();
    headers.add(
        "dispatch-generation", Integer.toString(generation).getBytes(StandardCharsets.US_ASCII));
    kafkaTemplate
        .send(
            new ProducerRecord<>(
                currentSourceTopic(), null, simulationId.toString(), value, headers))
        .get(15, java.util.concurrent.TimeUnit.SECONDS);
  }
}
