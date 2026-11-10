package me.veselin.probity.simulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.veselin.probity.common.domain.event.SimulationRequestedEvent;
import me.veselin.probity.simulation.persistence.SimulationRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Periodic publisher that reads pending outbox events and publishes them to Kafka.
 *
 * <p>Delivery is at-least-once and nothing is ever dropped by the publisher: * each row is locked,
 * leased with {@code claim_token + claimed_until}, and committed in a short {@code REQUIRES_NEW}
 * transaction before the blocking Kafka send. No row lock is held while waiting for the broker; *
 * the success/failure write is fenced on {@code claim_token}, so a publisher whose lease lapsed
 * cannot overwrite a newer claim; * a failed row keeps {@code error_count} (for alerting) and
 * {@code claimed_until} is pushed forward as exponential backoff, so the row stays queued and is
 * retried forever. There is no attempt limit that marks a row dead.
 *
 * <p>The claim and the outcome write are deliberately separate from the Kafka call: the scheduled
 * method itself is not transactional, so the {@code FOR UPDATE SKIP LOCKED} lock is released as
 * soon as the claim commits. The Kafka send then runs with no database transaction open, and the
 * fenced mark/failure is a second short transaction.
 */
@Service
@ConditionalOnProperty(name = "probity.simulation.worker.enabled", matchIfMissing = true)
@RequiredArgsConstructor
@Log4j2
public class OutboxPublisher {

  private final SimulationRepository simulationRepository;
  private final ObjectMapper objectMapper;
  private final @Qualifier("simulationKafkaTemplate") KafkaTemplate<
          String, SimulationRequestedEvent>
      kafkaTemplate;
  private final PlatformTransactionManager transactionManager;

  /** How many records to poll per cycle. */
  @Value("${probity.outbox.publish-batch-size:10}")
  private int publishBatchSize;

  /** Base backoff interval for failed publications (ms). Defaults to 5s. */
  @Value("${probity.outbox.initial-backoff-ms:5000}")
  private long initialBackoffMs;

  /** Maximum backoff interval for failed publications (ms). Defaults to 5m. */
  @Value("${probity.outbox.max-backoff-ms:300000}")
  private long maxBackoffMs;

  /** How long to wait for Kafka ack before giving up on a single record. */
  @Value("${probity.kafka.send-timeout-secs:30}")
  private int sendTimeoutSecs;

  /** How many consecutive errors before logging a WARN for operator intervention. */
  @Value("${probity.outbox.max-error-count:5}")
  private int maxErrorCount;

  /** Topic for simulation requested events, configurable per {@code application.yaml}. */
  @Value("${probity.kafka.simulation-requested-topic:simulation-requested}")
  private String simulationRequestedTopic;

  /**
   * Scheduled task that polls for pending outbox events and publishes them. Runs every {@code
   * probity.outbox.poll-interval-ms} milliseconds.
   *
   * <p>Uses {@code FOR UPDATE SKIP LOCKED} so that multiple publisher instances can safely
   * coordinate: each row is claimed exclusively by one instance, and other instances skip it. The
   * claim is committed before the blocking Kafka send, so no row lock is held across network I/O.
   *
   * <p>On failure, {@code error_count} is incremented and {@code claimed_until} is pushed forward
   * as exponential backoff. The row remains pending for a later poll cycle. The attempt threshold
   * is an alert only; rows are never dropped.
   */
  @Scheduled(
      fixedDelayString = "${probity.outbox.poll-interval-ms:10000}",
      initialDelayString = "${probity.outbox.initial-delay-ms:0}")
  public void publishPendingEvents() {
    for (int processed = 0; processed < publishBatchSize; processed++) {
      ClaimedRecord record = claimNextRecord();
      if (record == null) {
        log.debug("No pending outbox records to publish");
        return;
      }
      publish(record);
    }
  }

  /**
   * Locks and leases one claimable row in a short transaction.
   *
   * <p>The select and the claim must share a transaction: {@code FOR UPDATE SKIP LOCKED} is what
   * stops a concurrent publisher from choosing the same row, and the claim ({@code claimed_until})
   * is what stops it from choosing the row after this transaction commits. The transaction is
   * committed before the caller performs Kafka I/O.
   *
   * @return the claimed row, or {@code null} when nothing is claimable
   */
  private ClaimedRecord claimNextRecord() {
    return requiresNewTransaction()
        .execute(
            status -> {
              List<Object[]> rows = simulationRepository.lockPendingOutboxBatch(1, Instant.now());
              if (rows.isEmpty()) {
                return null;
              }

              Object[] row = rows.get(0);
              UUID id = (UUID) row[0];
              UUID simulationId = (UUID) row[1];
              String payload = (String) row[2];
              int dispatchGeneration = ((Number) row[3]).intValue();
              UUID claimToken = UUID.randomUUID();

              // The lease must outlive the blocking send, otherwise another publisher can
              // reclaim the row mid-send and publish the same event again.
              long leaseMs = Math.max(initialBackoffMs, (sendTimeoutSecs + 5L) * 1000L);
              Instant claimedUntil = Instant.now().plusMillis(leaseMs);

              int claimed =
                  simulationRepository.claimOutboxRecords(List.of(id), claimToken, claimedUntil);
              if (claimed == 0) {
                log.debug(
                    "Outbox row {} was claimed by another publisher between lock and claim", id);
                return null;
              }

              return new ClaimedRecord(id, simulationId, payload, dispatchGeneration, claimToken);
            });
  }

  /** Sends one claimed row and records the fenced outcome in its own short transaction. */
  private void publish(ClaimedRecord record) {
    try {
      SimulationRequestedEvent event =
          objectMapper.readValue(record.payload(), SimulationRequestedEvent.class);
      SimulationRequestedEvent generationStamped =
          event.withDispatchGeneration(record.dispatchGeneration());

      // Block until Kafka acknowledges the send (acks=all by default). At this point
      // the claim transaction has committed and no DB row lock or transaction is open.
      sendToKafka(record.simulationId(), generationStamped);

      int updated = simulationRepository.markOutboxPublished(record.id(), record.claimToken());
      if (updated == 0) {
        // The lease lapsed and a newer publisher owns the row. That publisher will
        // publish the event again, which is why delivery is at-least-once.
        log.debug(
            "Claim lost for outbox event {} after a successful send; "
                + "another publisher owns the row now",
            record.id());
      } else {
        log.debug("Published outbox event for simulation {}", record.simulationId());
      }
    } catch (Exception e) {
      recordFailure(record, e);
    }
  }

  /**
   * Sends the event and blocks until the broker acknowledges it.
   *
   * <p>Public so a test can substitute a broker outage without pointing the whole application at a
   * dead cluster; production always uses the real template.
   */
  public void sendToKafka(UUID simulationId, SimulationRequestedEvent event) throws Exception {
    Message<SimulationRequestedEvent> message =
        MessageBuilder.withPayload(event)
            .setHeader(KafkaHeaders.TOPIC, simulationRequestedTopic)
            .setHeader(KafkaHeaders.KEY, simulationId.toString())
            .setHeader(
                "dispatch-generation",
                Integer.toString(event.dispatchGeneration())
                    .getBytes(StandardCharsets.US_ASCII))
            .build();
    kafkaTemplate
        .send(message)
        .get(sendTimeoutSecs, TimeUnit.SECONDS);
  }

  /**
   * Records a failed publication and releases the lease for a later retry.
   *
   * <p>{@code error_count} is incremented and {@code claimed_until} is pushed forward as
   * exponential backoff. The row is never dropped: {@code published_at} stays {@code NULL}, so the
   * publisher will try again.
   */
  private void recordFailure(ClaimedRecord record, Exception cause) {
    int priorErrors = simulationRepository.findOutboxErrorCount(record.id()).orElse(0);
    long backoffMs = Math.min(maxBackoffMs, initialBackoffMs << Math.min(priorErrors, 20));
    Instant retryAt = Instant.now().plusMillis(backoffMs);

    int updated =
        simulationRepository.recordOutboxFailure(record.id(), record.claimToken(), retryAt);
    if (updated == 0) {
      log.debug(
          "Row {} was re-claimed during failure handling; " + "another publisher will retry it",
          record.id());
      return;
    }

    int errorCount = simulationRepository.findOutboxErrorCount(record.id()).orElse(0);
    if (errorCount >= maxErrorCount) {
      log.warn(
          "Outbox event for simulation {} has {} consecutive errors; "
              + "consider operator intervention. Record remains pending for retry.",
          record.simulationId(),
          errorCount);
    } else {
      log.debug(
          "Failed to publish outbox event for simulation {}: {}",
          record.simulationId(),
          cause.getMessage(),
          cause);
    }
  }

  private TransactionTemplate requiresNewTransaction() {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template;
  }

  /** A row whose lease is held by this publisher. */
  private record ClaimedRecord(
      UUID id, UUID simulationId, String payload, int dispatchGeneration, UUID claimToken) {}
}
