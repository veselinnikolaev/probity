package me.veselin.probity.simulation.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 1 (root cause D) — a failed execution must escape the listener and reach the configured
 * {@code DefaultErrorHandler}.
 *
 * <p>Regression this pins: the working tree had replaced the rethrow with a plain return. With
 * {@code manual_immediate} acknowledgement that return does not leave the record un-committed for
 * redelivery — the consumer's position has already advanced, so the next poll reads the following
 * record. No exception means {@code DefaultErrorHandler} never runs either, so the bounded retry
 * budget, the DLT write and the conditional {@code FAILED} transition are all bypassed and the
 * record is silently dropped.
 *
 * <p>The assertions are observable consequences of the handler running, not implementation
 * details:
 *
 * <ol>
 *   <li>three executions — one initial attempt plus the two configured retries;
 *   <li>a dead-letter record keyed on the simulation id — the recoverer ran;
 *   <li>the row is {@code FAILED} — which requires {@code markFailedForAttempt} to match the
 *       attempt id, so this also proves the attempt fence survived the rethrow;
 *   <li>the group's offset advanced past the record — {@code commitRecovered} did its job and the
 *       partition is not left wedged.
 * </ol>
 *
 * <p>This class publishes its own generation-stamped record instead of using the base fixture's
 * {@link #publish(Object, UUID)}; the fixture is repaired in Stage 3, and this test must be able to
 * prove the error-handler path independently of that repair.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SimulationAsyncFailurePropagationIntegrationTest extends BaseAsyncSimulationIntegrationTest {

  /** {@code KafkaConsumerConfig.MAX_RETRIES} is 2, so the handler makes 3 total attempts. */
  private static final int EXPECTED_ATTEMPTS = 3;

  private static final Duration DLT_WAIT = Duration.ofSeconds(20);

  private static final String GROUP_SUFFIX = "d-failure-propagation";

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    AsyncKafkaTestSupport.registerAsyncBroker(registry, true, GROUP_SUFFIX);
  }

  @Test
  @DisplayName("D: a failing execution reaches DefaultErrorHandler, exhausts the budget and dead-letters")
  void failingExecution_isRetriedDeadLetteredAndFailed() throws Exception {
    UUID id = seedPendingSimulation();

    // The failure is injected where real execution begins, i.e. after the claim.
    doThrow(new IllegalStateException("market data unavailable"))
        .when(portfolioQueryPort)
        .loadPortfolioWithPositionsAndAssets(any(), any());

    publishWithGeneration(validEventFor(id), id, 1);

    List<ConsumerRecord<String, byte[]>> dlt =
        AsyncKafkaTestSupport.drainDlt(DLT_WAIT, 1, GROUP_SUFFIX);
    Simulation row = simulationRepository.findById(id).orElseThrow();

    assertEquals(
        EXPECTED_ATTEMPTS,
        executionAttempts(),
        () ->
            "the error handler must retry the failed record on the configured budget before "
                + "dead-lettering it. "
                + describe(id, dlt, row));

    assertFalse(
        dlt.isEmpty(),
        () ->
            "an exhausted retry budget must produce a dead-letter record; an empty DLT means the "
                + "listener returned normally instead of throwing. "
                + describe(id, dlt, row));

    assertEquals(
        id.toString(),
        dlt.get(0).key(),
        () -> "the DLT record must stay keyed on the simulation id. " + describe(id, dlt, row));

    assertEquals(
        SimulationStatus.FAILED,
        row.getStatus(),
        () ->
            "the recoverer's conditional UPDATE requires the attempt id, so FAILED also proves "
                + "the attempt fence survived the rethrow. "
                + describe(id, dlt, row));

    assertFalse(
        committedOffset(currentGroupId()).isEmpty(),
        () ->
            "commitRecovered must commit the recovered record; an empty offset means the group "
                + "never moved and the partition would replay it forever. "
                + describe(id, dlt, row));
  }

  /**
   * Publishes with the generation carried in the header, mirroring what {@code OutboxPublisher}
   * sends. The header is the carrier the consumer prefers, so the envelope is stamped to agree.
   */
  protected void publishWithGeneration(Object value, UUID simulationId, int generation)
      throws Exception {
    RecordHeaders headers = new RecordHeaders();
    headers.add(
        "dispatch-generation", Integer.toString(generation).getBytes(StandardCharsets.US_ASCII));
    kafkaTemplate
        .send(new ProducerRecord<>(currentSourceTopic(), null, simulationId.toString(), value, headers))
        .get(15, java.util.concurrent.TimeUnit.SECONDS);
  }

  private String describe(
      UUID id, List<ConsumerRecord<String, byte[]>> dlt, Simulation row) {
    return "Observed: "
        + diagnostics(id, row)
        + " | dltRecords="
        + dlt.size()
        + " keys="
        + dlt.stream().map(ConsumerRecord::key).toList();
  }
}
