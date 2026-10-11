package me.veselin.probity.simulation.domain;

/**
 * Execution status of a simulation run.
 * Used for both synchronous and asynchronous execution paths.
 */
public enum SimulationStatus {
    /** Simulation created, not yet picked up for processing. */
    PENDING,

    /** Worker has picked up the simulation and is actively executing it. */
    PROCESSING,

    /** Simulation completed successfully with results persisted. */
    COMPLETED,

    /** Simulation failed during execution. */
    FAILED
}