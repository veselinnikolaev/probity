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

    /**
     * Maximum number of {@code allPaths} entries a response may carry (Q9a storage split,
     * docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §9/§10).
     *
     * <p><strong>Default {@code 0} = disabled: every stored path is returned.</strong>
     * That default is what keeps status/list/201 bodies byte-identical to the recorded
     * pre-Q9a contract — §10's verification gate measures exactly that — and it keeps the
     * choice of the real sample size N where §9 parked it, in PR-Q9b.
     *
     * <p>Values {@code > 0} return at most that many paths: a deterministic evenly-spaced
     * sample that always includes the paths holding the global minimum and maximum values,
     * so a y-range computed from the returned paths equals the full-data range (FanChart's
     * scale derives from path values). The sample bounds responses only — statistics,
     * percentileSeries and distribution are always computed from every path at run time and
     * are never re-derived from a sample.
     *
     * <p>Set per process ({@code probity.simulation.response-paths-sample-size}, env
     * {@code SIMULATION_RESPONSE_PATHS_SAMPLE_SIZE}) wherever responses are served: the
     * worker for the run-async DTO, the API for HTTP reads.
     */
    private int responsePathsSampleSize = 0;
}