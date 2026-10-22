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
     * Maximum number of {@code allPaths} entries the sampled-paths endpoint
     * ({@code GET /api/v1/simulations/{id}/paths}) serves when the caller does not pass an
     * explicit {@code n} (Q9b API split, docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §10).
     *
     * <p><strong>Default {@code 100} — an unmeasured first guess, not a measured optimum.</strong>
     * The owner's recorded answer bounds the sample at "at most N" and picks N here; 100 is
     * chosen as the smallest value that still draws a readable fan while cutting the paths
     * body from ≈ 3.90 MB to ≈ 390 KB, and it is deliberately a round, cheap-to-change
     * number. Changing it changes only the endpoint's default; storage and every computed
     * number are unaffected.
     *
     * <p>Sampling is deterministic: at most N evenly-spaced indices over the stored order,
     * always including the paths holding the global minimum and maximum values, so a
     * y-range computed from the returned paths equals the full-data range (FanChart's scale
     * derives from path values). {@code 0} or a negative value disables sampling and returns
     * every stored path.
     *
     * <p>Sampling bounds the response only — statistics, percentileSeries and distribution
     * are always computed from every path at run time and are never re-derived from a
     * sample. The status/list/result bodies carry no paths at all since Q9b.
     *
     * <p>Set per process ({@code probity.simulation.response-paths-sample-size}, env
     * {@code SIMULATION_RESPONSE_PATHS_SAMPLE_SIZE}) wherever the paths endpoint is served.
     */
    private int responsePathsSampleSize = 100;

    /**
     * Upper bound the paths endpoint accepts for its {@code n} query parameter. A request
     * above this bound — or above the number of stored paths — is rejected with 400 rather
     * than silently clamped, so a client cannot ask for a body larger than this ceiling.
     * Kept here (not in the controller) so the bounds move with the sampling configuration.
     */
    private int responsePathsMaxSampleSize = 1000;
}