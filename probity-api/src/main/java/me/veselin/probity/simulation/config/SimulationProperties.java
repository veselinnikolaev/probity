package me.veselin.probity.simulation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration for the asynchronous simulation worker.
 *
 * <p>Loaded from {@code application.yaml} under the {@code probity.simulation} namespace.
 */
@Data
@Component
@ConfigurationProperties(prefix = "probity.simulation")
public class SimulationProperties {

    /**
     * How long a claimed row stays claimed before another worker is allowed to take it.
     *
     * <p>This is the whole crash-recovery mechanism: a worker that dies mid-simulation leaves
     * the row {@code PROCESSING}, and this bound is what makes it claimable again. It is
     * also the upper bound on how long a parked record loops before it can be reclaimed, so
     * a lease that is too long delays recovery and one that is too short lets a slow but
     * healthy worker be preempted — a second worker would start the same simulation and the
     * first would then find its fence gone.
     *
     * <p>The Phase 4 invariant is {@code claim-lease > max.poll.interval.ms >= slowest
     * legitimate run}. The lease must stay above {@code max.poll.interval.ms} so a live
     * worker is never mistaken for a dead one, and above the slowest legitimate run so a
     * healthy worker is not preempted mid-flight.
     */
    private Duration claimLease = Duration.ofMinutes(12);

    /**
     * How long a partition is parked after an unclaimable (freshly {@code PROCESSING})
     * delivery before the same record is redelivered.
     *
     * <p>This spaces retries while another worker still holds the claim. It replaces the
     * previous whole-consumer {@code nack}: only the owning partition is paused, so sibling
     * partitions on the same consumer keep flowing.
     */
    private Duration unclaimableBackoff = Duration.ofSeconds(1);
}