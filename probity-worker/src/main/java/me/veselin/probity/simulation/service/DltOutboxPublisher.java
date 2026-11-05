package me.veselin.probity.simulation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.veselin.probity.simulation.persistence.SimulationDltOutboxJpaRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "probity.simulation.worker.enabled", matchIfMissing = true)
@RequiredArgsConstructor
@Log4j2
public class DltOutboxPublisher {

    private final SimulationDltOutboxJpaRepository dltRepository;
    private final @Qualifier("dltKafkaTemplate") KafkaTemplate<Object, byte[]> kafkaTemplate;
    private final PlatformTransactionManager transactionManager;

    @Value("${probity.kafka.simulation-requested-dlt-topic:simulation-requested.DLT}")
    private String dltTopic;

    @Value("${probity.dlt.publish-batch-size:10}")
    private int publishBatchSize;

    @Value("${probity.dlt.initial-backoff-ms:5000}")
    private long initialBackoffMs;

    @Value("${probity.dlt.max-backoff-ms:300000}")
    private long maxBackoffMs;

    @Value("${probity.kafka.send-timeout-secs:5}")
    private int sendTimeoutSecs;

    @Value("${probity.dlt.max-error-count:5}")
    private int maxErrorCount;

    @Scheduled(fixedDelayString = "${probity.dlt.poll-interval-ms:10000}",
            initialDelayString = "${probity.dlt.initial-delay-ms:0}")
    public void publishPendingEvents() {
        for (int processed = 0; processed < publishBatchSize; processed++) {
            ClaimedRecord record = claimNextRecord();
            if (record == null) {
                return;
            }
            publish(record);
        }
    }

    private ClaimedRecord claimNextRecord() {
        return requiresNewTransaction().execute(status -> {
            List<Object[]> rows = dltRepository.lockPendingBatch(1, Instant.now());
            if (rows.isEmpty()) {
                return null;
            }

            Object[] row = rows.get(0);
            UUID eventId = (UUID) row[0];
            String sourceTopic = (String) row[1];
            int sourcePartition = ((Number) row[2]).intValue();
            long sourceOffset = ((Number) row[3]).longValue();
            String sourceKey = (String) row[4];
            byte[] payload = row[5] == null ? null : ((byte[]) row[5]).clone();
            UUID claimToken = UUID.randomUUID();

            long leaseMs = Math.max(initialBackoffMs, (sendTimeoutSecs + 5L) * 1000L);
            Instant claimedUntil = Instant.now().plusMillis(leaseMs);
            if (dltRepository.claim(eventId, claimToken, claimedUntil, Instant.now()) == 0) {
                return null;
            }
            return new ClaimedRecord(eventId, sourceTopic, sourcePartition, sourceOffset,
                    sourceKey, payload, claimToken);
        });
    }

    private void publish(ClaimedRecord record) {
        try {
            ProducerRecord<Object, byte[]> producerRecord =
                    new ProducerRecord<>(dltTopic, record.sourceKey(), record.payload());
            producerRecord.headers()
                    .add("dlt_event_id", record.eventId().toString().getBytes(StandardCharsets.UTF_8))
                    .add("original_topic", record.sourceTopic().getBytes(StandardCharsets.UTF_8))
                    .add("original_partition", Integer.toString(record.sourcePartition())
                            .getBytes(StandardCharsets.UTF_8))
                    .add("original_offset", Long.toString(record.sourceOffset())
                            .getBytes(StandardCharsets.UTF_8))
                    .add("schema_version", "m3".getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(producerRecord)
                    .orTimeout(sendTimeoutSecs, TimeUnit.SECONDS)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            recordFailure(record, failure);
                            return;
                        }
                        if (dltRepository.markPublished(record.eventId(), record.claimToken()) == 0) {
                            log.debug("DLT claim {} expired before success mark; another publisher will retry",
                                    record.eventId());
                        }
                    });
        } catch (Exception failure) {
            recordFailure(record, failure);
        }
    }

    private void recordFailure(ClaimedRecord record, Throwable failure) {
        int priorAttempts = dltRepository.findById(record.eventId())
                .map(entity -> entity.getPublishAttemptCount())
                .orElse(0);
        long backoffMs = Math.min(maxBackoffMs,
                initialBackoffMs << Math.min(priorAttempts, 20));
        Instant retryAt = Instant.now().plusMillis(backoffMs);
        String message = failure.getMessage();
        if (message != null && message.length() > 4000) {
            message = message.substring(0, 4000);
        }

        if (dltRepository.recordFailure(record.eventId(), record.claimToken(), retryAt, message) == 0) {
            log.debug("DLT claim {} expired while recording failure", record.eventId());
            return;
        }

        int attempts = dltRepository.findById(record.eventId())
                .map(entity -> entity.getPublishAttemptCount())
                .orElse(0);
        if (attempts >= maxErrorCount) {
            log.warn("DLT event {} has {} consecutive publication failures; it remains queued",
                    record.eventId(), attempts);
        } else {
            log.debug("Failed to publish DLT event {}: {}", record.eventId(), message, failure);
        }
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private record ClaimedRecord(
            UUID eventId,
            String sourceTopic,
            int sourcePartition,
            long sourceOffset,
            String sourceKey,
            byte[] payload,
            UUID claimToken) {
    }
}
