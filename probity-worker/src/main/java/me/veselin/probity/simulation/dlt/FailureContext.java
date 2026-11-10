package me.veselin.probity.simulation.dlt;

import java.time.Instant;
import java.util.UUID;
import me.veselin.probity.common.domain.event.DispatchGeneration;

public record FailureContext(
    UUID dltEventId,
    UUID simulationId,
    UUID attemptId,
    String sourceTopic,
    int sourcePartition,
    long sourceOffset,
    String sourceKey,
    byte[] payload,
    String exceptionType,
    String errorDescription,
    Instant failedAt,
    Integer dispatchGeneration,
    DispatchGeneration.Source dispatchGenerationSource,
    String dispatchGenerationRaw) {

  public FailureContext {
    if (dltEventId == null
        || attemptId == null
        || sourceTopic == null
        || exceptionType == null
        || failedAt == null
        || dispatchGenerationSource == null) {
      throw new IllegalArgumentException("DLT failure context is missing required identity");
    }
    payload = payload == null ? null : payload.clone();
  }

  @Override
  public byte[] payload() {
    return payload == null ? null : payload.clone();
  }
}
