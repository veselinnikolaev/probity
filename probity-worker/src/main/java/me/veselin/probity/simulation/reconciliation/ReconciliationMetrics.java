package me.veselin.probity.simulation.reconciliation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Micrometer metrics exporter for the M2 Reconciliation Sweeper. */
@Component
public class ReconciliationMetrics {

  private final MeterRegistry registry;
  private final Timer sweepDurationTimer;

  private final AtomicReference<Instant> lastSuccessTimestamp =
      new AtomicReference<>(Instant.now());
  private final Map<String, AtomicLong> candidateGauges = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> stuckRowsGauges = new ConcurrentHashMap<>();

  private final AtomicLong outboxUnpublishedGauge = new AtomicLong(0);
  private final AtomicReference<Double> oldestUnpublishedAgeGauge = new AtomicReference<>(0.0);
  private final AtomicInteger maxErrorCountGauge = new AtomicInteger(0);
  private final AtomicReference<Double> oldestPublishedAgeGauge = new AtomicReference<>(0.0);
  private final AtomicInteger maxReconcileCountGauge = new AtomicInteger(0);

  public ReconciliationMetrics(MeterRegistry registry) {
    this.registry = registry;
    this.sweepDurationTimer =
        Timer.builder("probity_simulation_reconciliation_duration_seconds")
            .description("Duration of reconciliation sweep cycle in seconds")
            .register(registry);

    registry.gauge(
        "probity_simulation_reconciliation_last_success_timestamp_seconds",
        this,
        m -> m.getLastSuccessTimestamp().map(Instant::getEpochSecond).orElse(0L));

    registry.gauge("probity_simulation_outbox_unpublished", outboxUnpublishedGauge);
    registry.gauge(
        "probity_simulation_outbox_oldest_unpublished_age_seconds",
        oldestUnpublishedAgeGauge,
        AtomicReference::get);
    registry.gauge("probity_simulation_outbox_max_error_count", maxErrorCountGauge);
    registry.gauge(
        "probity_simulation_outbox_oldest_published_age_seconds",
        oldestPublishedAgeGauge,
        AtomicReference::get);
    registry.gauge("probity_simulation_reconcile_count_max", maxReconcileCountGauge);
  }

  public void recordDuration(Duration duration) {
    sweepDurationTimer.record(duration);
  }

  public void markSuccess() {
    lastSuccessTimestamp.set(Instant.now());
  }

  public java.util.Optional<Instant> getLastSuccessTimestamp() {
    return java.util.Optional.ofNullable(lastSuccessTimestamp.get());
  }

  public void setCandidateCount(String state, long count) {
    candidateGauges
        .computeIfAbsent(
            state,
            s -> {
              AtomicLong gauge = new AtomicLong(count);
              registry.gauge(
                  "probity_simulation_reconciliation_candidates", Tags.of("state", s), gauge);
              return gauge;
            })
        .set(count);
  }

  public void setStuckRowsCount(String status, long count) {
    stuckRowsGauges
        .computeIfAbsent(
            status,
            s -> {
              AtomicLong gauge = new AtomicLong(count);
              registry.gauge("probity_simulation_stuck_rows", Tags.of("status", s), gauge);
              return gauge;
            })
        .set(count);
  }

  public void setOutboxUnpublished(long count) {
    outboxUnpublishedGauge.set(count);
  }

  public void setOldestUnpublishedAge(double ageSeconds) {
    oldestUnpublishedAgeGauge.set(ageSeconds);
  }

  public void setMaxErrorCount(int count) {
    maxErrorCountGauge.set(count);
  }

  public void setOldestPublishedAge(double ageSeconds) {
    oldestPublishedAgeGauge.set(ageSeconds);
  }

  public void setMaxReconcileCount(int count) {
    maxReconcileCountGauge.set(count);
  }

  public void recordAction(String state, String action, String outcome) {
    Counter.builder("probity_simulation_reconciliation_actions_total")
        .tags("state", state, "action", action, "outcome", outcome)
        .register(registry)
        .increment();
  }

  public void recordAlert(String reason) {
    Counter.builder("probity_simulation_reconciliation_alerts_total")
        .tag("reason", reason)
        .register(registry)
        .increment();
  }
}
