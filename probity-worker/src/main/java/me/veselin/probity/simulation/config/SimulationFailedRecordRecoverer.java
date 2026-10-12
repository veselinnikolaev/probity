package me.veselin.probity.simulation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.simulation.persistence.SimulationRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
public final class SimulationFailedRecordRecoverer implements ConsumerRecordRecoverer {

    private final DeadLetterPublishingRecoverer delegate;
    private final SimulationRepository repository;
    private final ObjectMapper objectMapper;
    private final Counter dltPublishFailures;

    public SimulationFailedRecordRecoverer(DeadLetterPublishingRecoverer delegate,
                                           SimulationRepository repository,
                                           ObjectMapper objectMapper,
                                           MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.dltPublishFailures = Counter.builder("probity.simulation.dlt.publish.failures")
                .description("Count of times publishing to the simulation DLT failed")
                .register(meterRegistry);
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        UUID simulationId = null;
        try {
            String key = record.key() == null ? null : record.key().toString();
            if (key != null) {
                simulationId = UUID.fromString(key);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Failed to parse simulationId from record key: {}", record.key(), e);
        }

        // (1) DB first: mark the row FAILED if it is still PENDING
        if (simulationId != null) {
            boolean marked = repository.markFailedIfPending(simulationId);
            if (!marked) {
                log.debug("Did not mark simulation {} as FAILED (not PENDING, deleted, or missing)", simulationId);
            } else {
                log.info("Marked simulation {} as FAILED before DLT publish", simulationId);
            }
        }

        try {
            // (2) then DLT: coerce value to byte[] and delegate
            ConsumerRecord<?, byte[]> byteArrayRecord = withByteArrayValue(record);
            delegate.accept(byteArrayRecord, exception);
        } catch (Exception e) {
            dltPublishFailures.increment();
            if (simulationId != null) {
                log.error("Failed to publish simulation {} to DLT; rethrowing so offset is not committed: {}", simulationId, e.getMessage(), e);
            } else {
                log.error("Failed to publish record to DLT; rethrowing so offset is not committed: {}", e.getMessage(), e);
            }
            throw e;
        }
    }

    /**
     * Coerce the consumer record's value to a byte array while preserving key, headers,
     * topic, partition, offset and timestamp.
     */
    private ConsumerRecord<?, byte[]> withByteArrayValue(ConsumerRecord<?, ?> record) {
        byte[] coercedValue = coerceToBytes(record.value());
        Headers headers = copyHeaders(record.headers());
        return new ConsumerRecord<>(
                record.topic(),
                record.partition(),
                record.offset(),
                record.timestamp(),
                record.timestampType(),
                record.serializedKeySize(),
                record.serializedValueSize(),
                record.key(),
                coercedValue,
                headers,
                record.leaderEpoch()
        );
    }

    private byte[] coerceToBytes(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        if (value instanceof String s) {
            return s.getBytes(StandardCharsets.UTF_8);
        }
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            log.warn("Failed to serialize value to bytes; falling back to toString()", e);
            String s = value.toString();
            return s == null ? null : s.getBytes(StandardCharsets.UTF_8);
        }
    }

    private Headers copyHeaders(Headers source) {
        if (source == null) {
            return new RecordHeaders();
        }
        RecordHeaders target = new RecordHeaders();
        for (Header h : source) {
            // Keep the original instances: the ErrorHandlingDeserializer records the failed
            // payload in a package-private DeserializationExceptionHeader, and the
            // DeadLetterPublishingRecoverer refuses to read it back if it is rebuilt as a
            // plain RecordHeader ("Foreign deserialization exception header ... ignored;
            // possible attack?"), which would publish a null-value DLT record.
            target.add(h);
        }
        return target;
    }
}