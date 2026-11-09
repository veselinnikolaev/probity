package me.veselin.probity.simulation.reconciliation;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator health indicator for the Reconciliation Sweeper.
 *
 * <p>Exposes last success time, duration, and stalled state. Kept outside of the readiness group so
 * that a stalled sweeper does not take a healthy Kafka consumer or API node out of service.
 */
@Component("reconciliationHealthIndicator")
public class ReconciliationHealthIndicator implements HealthIndicator {

  private final ReconciliationProperties properties;
  private final ReconciliationMetrics metrics;
  private volatile long lastCycleDurationMs = 0;
  private volatile int lastCandidatesCount = 0;
  private volatile int lastActionsCount = 0;

  public ReconciliationHealthIndicator(
      ReconciliationProperties properties, ReconciliationMetrics metrics) {
    this.properties = properties;
    this.metrics = metrics;
  }

  public void recordCycleStats(long durationMs, int candidatesCount, int actionsCount) {
    this.lastCycleDurationMs = durationMs;
    this.lastCandidatesCount = candidatesCount;
    this.lastActionsCount = actionsCount;
  }

  @Override
  public Health health() {
    Health.Builder builder = Health.up();
    if (!properties.isEnabled()) {
      builder = Health.up().withDetail("status", "DISABLED");
    }

    Instant lastSuccess = metrics.getLastSuccessTimestamp().orElse(null);
    if (lastSuccess == null) {
      return builder.withDetail("reason", "No sweep cycle has completed yet").build();
    }

    long stalledThresholdSeconds = Math.max(300L, (properties.getSweepIntervalMs() / 1000) * 5);
    long elapsedSeconds = Duration.between(lastSuccess, Instant.now()).getSeconds();
    boolean stalled = properties.isEnabled() && elapsedSeconds > stalledThresholdSeconds;

    if (stalled) {
      builder = Health.status("OUT_OF_SERVICE");
    }

    return builder
        .withDetail("lastSuccessfulCycleAt", lastSuccess.toString())
        .withDetail("elapsedSecondsSinceLastSuccess", elapsedSeconds)
        .withDetail("lastCycleDurationMs", lastCycleDurationMs)
        .withDetail("lastCandidatesCount", lastCandidatesCount)
        .withDetail("lastActionsCount", lastActionsCount)
        .withDetail("stalled", stalled)
        .build();
  }
}
