package me.veselin.probity.simulation.reconciliation;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration properties for the M2 Reconciliation Sweeper. */
@Data
@Component
@ConfigurationProperties(prefix = "probity.reconciliation")
public class ReconciliationProperties {

  /**
   * Whether the reconciliation sweeper is enabled. Defaults to false; enabled explicitly in worker
   * application config.
   */
  private boolean enabled = false;

  /** Interval in ms between successive sweep cycles. */
  private long sweepIntervalMs = 60_000L;

  /** Initial delay in ms before the first sweep cycle. */
  private long initialDelayMs = 60_000L;

  /** Grace duration before a PENDING simulation with published outbox is considered stuck. */
  private Duration pendingGrace = Duration.ofMinutes(15);

  /** Grace duration before a PROCESSING simulation is considered stale/abandoned. */
  private Duration processingGrace = Duration.ofMinutes(30);

  /** Base cooldown duration per outbox record to prevent rapid repeat reconciliation. */
  private Duration reconcileCooldown = Duration.ofMinutes(30);

  /** Maximum candidates fetched per detection query. */
  private int scanBatchSize = 50;

  /** Maximum state repair actions executed per sweep cycle. */
  private int maxActionsPerCycle = 25;

  /** Grace duration before an unpublished orphan outbox record triggers an alert. */
  private Duration orphanGrace = Duration.ofHours(1);

  /** Number of reconciliations on a single row before firing repeat alert. */
  private int alertAfterReconciles = 3;

  /** Number of reconciliations on a single row before severe escalation. */
  private int severeAfterReconciles = 10;

  /** Upper bound cap for exponential backoff cooldown. */
  private Duration maxCooldown = Duration.ofHours(6);

  /** Statement timeout in ms for reconciliation DB transactions. */
  private long statementTimeoutMs = 5000L;

  /** Lock timeout in ms for row locks in reconciliation DB transactions. */
  private long lockTimeoutMs = 2000L;
}
