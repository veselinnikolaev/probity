package me.veselin.probity.simulation.config;

import static org.apache.commons.logging.LogFactory.getLog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.simulation.dlt.FailureContext;
import me.veselin.probity.simulation.dlt.SimulationDltEventWriter;
import me.veselin.probity.simulation.dlt.WriteResult;
import me.veselin.probity.common.domain.event.DispatchGeneration;
import me.veselin.probity.common.domain.event.SimulationRequestedEvent;
import me.veselin.probity.simulation.config.SimulationProperties;
import me.veselin.probity.simulation.exception.SimulationAttemptFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public final class SimulationFailedRecordRecoverer implements ConsumerRecordRecoverer {

  private static final LogAccessor DESERIALIZATION_LOG =
      new LogAccessor(getLog(SimulationFailedRecordRecoverer.class));

  private final SimulationDltEventWriter writer;
  private final ObjectMapper objectMapper;
  private final SimulationProperties properties;

  public SimulationFailedRecordRecoverer(
      SimulationDltEventWriter writer,
      ObjectMapper objectMapper,
      SimulationProperties properties) {
    this.writer = writer;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public void accept(ConsumerRecord<?, ?> record, Exception exception) {
    boolean deserializationFailure = isDeserializationFailure(record, exception);
    SimulationAttemptFailedException attemptFailure = findAttemptFailure(exception);

    UUID simulationId =
        attemptFailure == null ? simulationIdFromRecord(record) : attemptFailure.simulationId();
    UUID attemptId =
        attemptFailure == null ? syntheticAttemptId(record) : attemptFailure.attemptId();
    String rawHeader = generationHeader(record);
    Integer envelopeGeneration =
        record.value() instanceof SimulationRequestedEvent requested
            ? requested.dispatchGeneration()
            : null;
    DispatchGeneration.Resolution generation =
        DispatchGeneration.resolve(
            rawHeader, envelopeGeneration, properties.isLegacyGenerationDefault());

    if (!deserializationFailure && attemptFailure == null) {
      throw new IllegalStateException(
          "Simulation listener failure reached recovery without an attempt fence", exception);
    }

    FailureContext context =
        new FailureContext(
            UUID.randomUUID(),
            simulationId,
            attemptId,
            record.topic(),
            record.partition(),
            record.offset(),
            record.key() == null ? null : record.key().toString(),
            payloadBytes(record, deserializationFailure),
            exception.getClass().getName(),
            safeDescription(exception),
            Instant.now(),
            generation.value(),
            generation.source(),
            rawHeader != null
                ? truncate(rawHeader)
                : envelopeGeneration == null ? null : Integer.toString(envelopeGeneration));

    WriteResult result;
    if (simulationId == null) {
      result = writer.recordUnattributableFailure(context);
    } else if (deserializationFailure) {
      result = writer.recordUnclaimedFailure(context);
    } else {
      result = writer.recordAttemptFailure(context);
    }

    if (result == WriteResult.NOT_OWNER) {
      log.debug(
          "Did not record DLT event for source {}-{}@{}; simulation is no longer owned",
          record.topic(),
          record.partition(),
          record.offset());
    } else {
      log.info(
          "Persisted durable DLT event {} for source {}-{}@{}",
          context.dltEventId(),
          record.topic(),
          record.partition(),
          record.offset());
    }
  }

  private boolean isDeserializationFailure(ConsumerRecord<?, ?> record, Exception exception) {
    if (exception instanceof org.apache.kafka.common.errors.SerializationException) {
      return true;
    }
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current instanceof DeserializationException) {
        return true;
      }
    }
    return SerializationUtils.getExceptionFromHeader(
            record, SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER, DESERIALIZATION_LOG)
        != null;
  }

  private byte[] payloadBytes(ConsumerRecord<?, ?> record, boolean deserializationFailure) {
    if (deserializationFailure) {
      DeserializationException deserialization =
          SerializationUtils.getExceptionFromHeader(
              record, SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER, DESERIALIZATION_LOG);
      if (deserialization != null && deserialization.getData() != null) {
        return deserialization.getData();
      }
    }
    if (record.value() == null) {
      return null;
    }
    if (record.value() instanceof byte[] bytes) {
      return bytes.clone();
    }
    if (record.value() instanceof String value) {
      return value.getBytes(StandardCharsets.UTF_8);
    }
    try {
      return objectMapper.writeValueAsBytes(record.value());
    } catch (Exception serializationFailure) {
      throw new IllegalStateException(
          "Could not serialize recovered Kafka value", serializationFailure);
    }
  }

  private UUID simulationIdFromRecord(ConsumerRecord<?, ?> record) {
    if (record.key() == null) {
      return null;
    }
    try {
      return UUID.fromString(record.key().toString());
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private UUID syntheticAttemptId(ConsumerRecord<?, ?> record) {
    return UUID.nameUUIDFromBytes(
        ("dlt:" + record.topic() + ":" + record.partition() + ":" + record.offset())
            .getBytes(StandardCharsets.UTF_8));
  }

  private SimulationAttemptFailedException findAttemptFailure(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof SimulationAttemptFailedException attemptFailure) {
        return attemptFailure;
      }
    }
    return null;
  }

  private String safeDescription(Throwable failure) {
    String message = failure.getMessage();
    if (message == null) {
      return null;
    }
    return message.length() <= 4000 ? message : message.substring(0, 4000);
  }

  private String generationHeader(ConsumerRecord<?, ?> record) {
    org.apache.kafka.common.header.Header header =
        record.headers().lastHeader("dispatch-generation");
    return header == null ? null : new String(header.value(), StandardCharsets.US_ASCII);
  }

  private String truncate(String value) {
    return value.length() <= 64 ? value : value.substring(0, 64);
  }
}
