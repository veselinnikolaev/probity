package me.veselin.probity.simulation.exception;

import java.util.UUID;

/** Preserves the simulation claim attempt that produced a listener failure. */
public final class SimulationAttemptFailedException extends RuntimeException {

  private final UUID simulationId;
  private final UUID attemptId;

  public SimulationAttemptFailedException(UUID simulationId, UUID attemptId, Throwable cause) {
    super(cause);
    this.simulationId = simulationId;
    this.attemptId = attemptId;
  }

  public UUID simulationId() {
    return simulationId;
  }

  public UUID attemptId() {
    return attemptId;
  }
}
