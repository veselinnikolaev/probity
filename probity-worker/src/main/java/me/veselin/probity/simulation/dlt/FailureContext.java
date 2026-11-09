package me.veselin.probity.simulation.dlt;

import java.time.Instant;
import java.util.UUID;

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
    Instant failedAt) {

  public FailureContext {
    if (dltEventId == null
        || attemptId == null
        || sourceTopic == null
        || exceptionType == null
        || failedAt == null) {
      throw new IllegalArgumentException("DLT failure context is missing required identity");
    }
    payload = payload == null ? null : payload.clone();
  }

  @Override
  public byte[] payload() {
    return payload == null ? null : payload.clone();
  }
}
