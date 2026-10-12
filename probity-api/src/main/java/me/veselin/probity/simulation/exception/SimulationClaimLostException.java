package me.veselin.probity.simulation.exception;

import java.time.Instant;
import java.util.UUID;

/**
 * A worker tried to write back a result for a row it no longer owns.
 *
 * <p>Raised when a fenced {@code complete} or {@code release} affects zero rows, meaning the
 * lease lapsed while the simulation was running and a peer has since reclaimed the row. The
 * worker must then stop without side effects: no completion event, no retry of the same row,
 * because a peer is already running it.
 *
 * <p>Not a defect in the simulation and not retryable as-is — the consumer turns it into an
 * {@code ERROR} log and an acknowledgement.
 */
public class SimulationClaimLostException extends RuntimeException {

    private final UUID simulationId;
    private final Instant fenceToken;

    public SimulationClaimLostException(UUID simulationId, Instant fenceToken, String operation) {
        super("Simulation " + simulationId + ": " + operation + " affected 0 rows — the claim "
                + "taken at " + fenceToken + " is no longer current, so another worker owns this "
                + "row. Stopping without side effects.");
        this.simulationId = simulationId;
        this.fenceToken = fenceToken;
    }

    public UUID getSimulationId() {
        return simulationId;
    }

    public Instant getFenceToken() {
        return fenceToken;
    }
}
