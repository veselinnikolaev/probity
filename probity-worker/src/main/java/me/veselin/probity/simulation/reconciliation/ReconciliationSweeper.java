package me.veselin.probity.simulation.reconciliation;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.veselin.probity.simulation.domain.SimulationStatus;
import me.veselin.probity.simulation.persistence.SimulationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * M2 Reconciliation Sweeper.
 * <p>
 * Detects DB-visible stranded async simulation workflows and repairs state so that
 * the existing {@code OutboxPublisher} and {@code SimulationRequestedEventConsumer} can
 * resume responsibility for delivery and execution.
 * <p>
 * Invariants:
 * <ul>
 *   <li>Never publishes directly to Kafka.</li>
 *   <li>Never executes simulations.</li>
 *   <li>Never creates or deletes simulations or outbox records.</li>
 *   <li>Never writes a simulation to FAILED.</li>
 *   <li>Atomic and fenced state transitions.</li>
 *   <li>Missing/orphaned outbox rows are alert-only.</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "probity.simulation.worker.enabled", matchIfMissing = true)
@RequiredArgsConstructor
@Log4j2
public class ReconciliationSweeper {

    private final SimulationRepository simulationRepository;
    private final ReconciliationProperties properties;
    private final ReconciliationMetrics metrics;
    private final ReconciliationHealthIndicator healthIndicator;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;

    private enum CandidateType {
        Q1_PENDING_NO_DELIVERY,
        Q2_STALE_PROCESSING
    }

    private record CandidateWrapper(
            SimulationRepository.ReconciliationCandidate candidate,
            CandidateType type
    ) {
        public Instant stateSince() {
            return candidate.stateSince() != null ? candidate.stateSince() : Instant.EPOCH;
        }
    }

    /**
     * Scheduled entry point running every {@code probity.reconciliation.sweep-interval-ms}.
     */
    @Scheduled(
            fixedDelayString = "${probity.reconciliation.sweep-interval-ms:60000}",
            initialDelayString = "${probity.reconciliation.initial-delay-ms:60000}"
    )
    public void scheduledSweep() {
        if (!properties.isEnabled()) {
            log.trace("Reconciliation sweeper is disabled by configuration");
            return;
        }
        sweepOnce();
    }

    /**
     * Executes one complete reconciliation cycle.
     * Public for direct and deterministic test execution.
     *
     * @return total repair actions successfully applied in this cycle
     */
    public int sweepOnce() {
        Instant startedAt = Instant.now();
        int actions = 0;
        int totalCandidates = 0;

        try {
            double pendingGraceSec = properties.getPendingGrace().toSeconds();
            double processingGraceSec = properties.getProcessingGrace().toSeconds();
            double cooldownSec = properties.getReconcileCooldown().toSeconds();
            int batchSize = properties.getScanBatchSize();
            int maxActions = properties.getMaxActionsPerCycle();

            // 1. Detect Q1 & Q2 candidates
            List<SimulationRepository.ReconciliationCandidate> q1List =
                    simulationRepository.findPendingSimulationsWithoutDelivery(pendingGraceSec, cooldownSec, batchSize);
            List<SimulationRepository.ReconciliationCandidate> q2List =
                    simulationRepository.findStaleProcessingSimulationsWithOutbox(processingGraceSec, cooldownSec, batchSize);

            metrics.setCandidateCount("pending_no_delivery", q1List.size());
            metrics.setCandidateCount("stale_processing", q2List.size());

            // Merge and sort candidates by state_since ascending
            List<CandidateWrapper> candidates = new ArrayList<>();
            for (var c : q1List) {
                candidates.add(new CandidateWrapper(c, CandidateType.Q1_PENDING_NO_DELIVERY));
            }
            for (var c : q2List) {
                candidates.add(new CandidateWrapper(c, CandidateType.Q2_STALE_PROCESSING));
            }
            candidates.sort(Comparator.comparing(CandidateWrapper::stateSince));
            totalCandidates = candidates.size();

            // 2. Execute reconciliation actions up to maxActionsPerCycle
            for (CandidateWrapper wrapper : candidates) {
                if (actions >= maxActions) {
                    break;
                }
                try {
                    if (wrapper.type() == CandidateType.Q1_PENDING_NO_DELIVERY) {
                        if (resetOutboxForPending(wrapper.candidate())) {
                            actions++;
                        }
                    } else {
                        if (resetProcessing(wrapper.candidate())) {
                            actions++;
                        }
                    }
                } catch (CannotAcquireLockException | OptimisticLockingFailureException e) {
                    log.warn("Reconciliation lock skipped for simulationId={}: {}",
                            wrapper.candidate().simulationId(), e.getMessage());
                    metrics.recordAction(
                            wrapper.type() == CandidateType.Q1_PENDING_NO_DELIVERY ? "pending_no_delivery" : "stale_processing",
                            "lock_contention",
                            "skipped");
                } catch (DataAccessException e) {
                    log.warn("Reconciliation DB error for simulationId={}: {}",
                            wrapper.candidate().simulationId(), e.getMessage());
                    metrics.recordAction(
                            wrapper.type() == CandidateType.Q1_PENDING_NO_DELIVERY ? "pending_no_delivery" : "stale_processing",
                            "db_error",
                            "failed");
                } catch (Exception e) {
                    log.error("Unexpected error reconciling simulationId={}",
                            wrapper.candidate().simulationId(), e);
                    metrics.recordAction(
                            wrapper.type() == CandidateType.Q1_PENDING_NO_DELIVERY ? "pending_no_delivery" : "stale_processing",
                            "unknown_error",
                            "failed");
                }
            }

            // 3. Alert-only scans (Q3, Q4, Q5)
            runAlertScans();

            // 4. Update metrics gauges
            updateAggregateMetrics();

            // Mark cycle success and record duration
            metrics.markSuccess();
            Duration cycleDuration = Duration.between(startedAt, Instant.now());
            metrics.recordDuration(cycleDuration);
            healthIndicator.recordCycleStats(cycleDuration.toMillis(), totalCandidates, actions);

        } catch (Exception e) {
            log.error("Reconciliation sweep cycle failed with error", e);
            metrics.recordAlert("query_failed");
        }

        return actions;
    }

    /**
     * Q1 action: Resets outbox published_at to NULL for a PENDING simulation (C4 recovery).
     */
    public boolean resetOutboxForPending(SimulationRepository.ReconciliationCandidate candidate) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        return Boolean.TRUE.equals(tx.execute(status -> {
            applyStatementAndLockTimeouts();
            Optional<SimulationRepository.OutboxReconciliationState> outboxOpt =
                    simulationRepository.lockOutboxForReconciliation(candidate.simulationId());
            if (outboxOpt.isEmpty()) {
                metrics.recordAction("pending_no_delivery", "reset_outbox", "skipped");
                return false;
            }

            SimulationRepository.OutboxReconciliationState outbox = outboxOpt.get();
            Instant now = Instant.now();

            if (outbox.claimedUntil() != null && outbox.claimedUntil().isAfter(now)) {
                metrics.recordAction("pending_no_delivery", "reset_outbox", "skipped");
                return false;
            }

            long effectiveCooldownSeconds = calculateEffectiveCooldown(outbox.reconcileCount());
            if (outbox.lastReconciledAt() != null &&
                    outbox.lastReconciledAt().plusSeconds(effectiveCooldownSeconds).isAfter(now)) {
                metrics.recordAction("pending_no_delivery", "reset_outbox", "skipped");
                return false;
            }

            if (outbox.publishedAt() == null) {
                metrics.recordAction("pending_no_delivery", "reset_outbox", "skipped");
                return false;
            }

            int updated = simulationRepository.resetOutboxPublishedStatus(outbox.outboxId());
            if (updated == 1) {
                int newReconcileCount = outbox.reconcileCount() + 1;
                metrics.recordAction("pending_no_delivery", "reset_outbox", "applied");
                logReconcileAction(candidate.simulationId(), "PENDING", "PENDING", outbox.outboxId(),
                        "reset_outbox", candidate.stateSince(), newReconcileCount, "pending_no_delivery", "applied");
                if (newReconcileCount >= properties.getAlertAfterReconciles()) {
                    metrics.recordAlert("repeat");
                    log.warn("Simulation {} reached {} reconcile cycles (reconcile loop)",
                            candidate.simulationId(), newReconcileCount);
                }
                return true;
            } else {
                metrics.recordAction("pending_no_delivery", "reset_outbox", "skipped");
                return false;
            }
        }));
    }

    /**
     * Q2 action: Atomically resets a stale PROCESSING simulation to PENDING and outbox to unpublished (C5 recovery).
     */
    public boolean resetProcessing(SimulationRepository.ReconciliationCandidate candidate) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        return Boolean.TRUE.equals(tx.execute(status -> {
            applyStatementAndLockTimeouts();
            Optional<SimulationRepository.OutboxReconciliationState> outboxOpt =
                    simulationRepository.lockOutboxForReconciliation(candidate.simulationId());
            if (outboxOpt.isEmpty()) {
                // S8: Stale PROCESSING with no outbox row -> ALERT ONLY, never strand a PENDING row!
                metrics.recordAction("stale_processing", "reset_simulation", "skipped");
                metrics.recordAlert("missing_outbox");
                log.warn("Stale PROCESSING simulation {} has no outbox row. Alert only, skipping reset.",
                        candidate.simulationId());
                return false;
            }

            SimulationRepository.OutboxReconciliationState outbox = outboxOpt.get();
            Instant now = Instant.now();
            boolean resetOutbox = false;

            if (outbox.publishedAt() != null) {
                if (outbox.claimedUntil() != null && outbox.claimedUntil().isAfter(now)) {
                    metrics.recordAction("stale_processing", "reset_simulation", "skipped");
                    return false;
                }
                long effectiveCooldownSeconds = calculateEffectiveCooldown(outbox.reconcileCount());
                if (outbox.lastReconciledAt() != null &&
                        outbox.lastReconciledAt().plusSeconds(effectiveCooldownSeconds).isAfter(now)) {
                    metrics.recordAction("stale_processing", "reset_simulation", "skipped");
                    return false;
                }
                int outboxUpdated = simulationRepository.resetOutboxPublishedStatus(outbox.outboxId());
                if (outboxUpdated == 1) {
                    resetOutbox = true;
                }
            }

            int simUpdated = simulationRepository.resetProcessingSimulationToPending(
                    candidate.simulationId(), candidate.stateSince());
            if (simUpdated == 0) {
                // Fenced write failed: simulation completed or claimed by peer between scan and lock
                status.setRollbackOnly();
                metrics.recordAction("stale_processing", "reset_simulation", "skipped");
                return false;
            }

            String actionName = resetOutbox ? "reset_simulation+reset_outbox" : "reset_simulation";
            int newReconcileCount = outbox.reconcileCount() + (resetOutbox ? 1 : 0);
            metrics.recordAction("stale_processing", actionName, "applied");
            logReconcileAction(candidate.simulationId(), "PROCESSING", "PENDING", outbox.outboxId(),
                    actionName, candidate.stateSince(), newReconcileCount, "stale_processing", "applied");

            if (newReconcileCount >= properties.getAlertAfterReconciles()) {
                metrics.recordAlert("repeat");
                log.warn("Simulation {} reached {} reconcile cycles (reconcile loop)",
                        candidate.simulationId(), newReconcileCount);
            }
            return true;
        }));
    }

    private void applyStatementAndLockTimeouts() {
        try {
            jdbcTemplate.execute("SET LOCAL statement_timeout = '" + properties.getStatementTimeoutMs() + "ms'");
            jdbcTemplate.execute("SET LOCAL lock_timeout = '" + properties.getLockTimeoutMs() + "ms'");
        } catch (Exception e) {
            log.debug("Could not set local timeouts: {}", e.getMessage());
        }
    }

    private long calculateEffectiveCooldown(int reconcileCount) {
        long baseCooldownSec = properties.getReconcileCooldown().toSeconds();
        long maxCooldownSec = properties.getMaxCooldown().toSeconds();
        long multiplier = 1L << Math.min(reconcileCount, 30);
        return Math.min(maxCooldownSec, baseCooldownSec * multiplier);
    }

    private void runAlertScans() {
        try {
            double pendingGraceSec = properties.getPendingGrace().toSeconds();
            double orphanGraceSec = properties.getOrphanGrace().toSeconds();
            int batchSize = properties.getScanBatchSize();

            // Q3: PENDING with no outbox
            List<SimulationRepository.MissingOutboxAlert> q3 =
                    simulationRepository.findPendingSimulationsWithoutOutbox(pendingGraceSec, batchSize);
            metrics.setCandidateCount("pending_no_outbox", q3.size());
            for (var alert : q3) {
                metrics.recordAlert("missing_outbox");
                log.warn("Simulation {} is PENDING with no outbox row (stateSince={})",
                        alert.simulationId(), alert.stateSince());
            }

            // Q4: Orphan unpublished outbox
            List<SimulationRepository.OrphanOutboxAlert> q4 =
                    simulationRepository.findOrphanOutboxRecords(orphanGraceSec, batchSize);
            metrics.setCandidateCount("orphan_outbox", q4.size());
            for (var alert : q4) {
                metrics.recordAlert("orphan_outbox");
                log.warn("Orphan outbox row {} (simulationId={}, createdAt={})",
                        alert.outboxId(), alert.simulationId(), alert.createdAt());
            }

            // Q5: Reconcile loop
            List<SimulationRepository.ReconcileLoopAlert> q5 =
                    simulationRepository.findReconcileLoopRecords(
                            properties.getAlertAfterReconciles(),
                            properties.getReconcileCooldown().toSeconds() * 2,
                            batchSize);
            for (var alert : q5) {
                metrics.recordAlert("repeat");
                if (alert.reconcileCount() >= properties.getSevereAfterReconciles()) {
                    log.error("Severe reconcile loop on simulationId={} (reconcileCount={})",
                            alert.simulationId(), alert.reconcileCount());
                } else {
                    log.warn("Reconcile loop detected on simulationId={} (reconcileCount={})",
                            alert.simulationId(), alert.reconcileCount());
                }
            }
        } catch (Exception e) {
            log.warn("Alert-only scan encountered error: {}", e.getMessage());
            metrics.recordAlert("query_failed");
        }
    }

    private void updateAggregateMetrics() {
        try {
            metrics.setStuckRowsCount("PENDING",
                    simulationRepository.countStuckSimulations(SimulationStatus.PENDING, properties.getPendingGrace().toSeconds()));
            metrics.setStuckRowsCount("PROCESSING",
                    simulationRepository.countStuckSimulations(SimulationStatus.PROCESSING, properties.getProcessingGrace().toSeconds()));
            metrics.setOutboxUnpublished(simulationRepository.countUnpublishedOutbox());
            metrics.setOldestUnpublishedAge(simulationRepository.getOldestUnpublishedOutboxAgeSeconds());
            metrics.setMaxErrorCount(simulationRepository.getMaxOutboxErrorCount());
            metrics.setOldestPublishedAge(simulationRepository.getOldestPublishedOutboxAgeSeconds());
            metrics.setMaxReconcileCount(simulationRepository.getMaxReconcileCount());
        } catch (Exception e) {
            log.debug("Failed to update aggregate metrics: {}", e.getMessage());
        }
    }

    private void logReconcileAction(UUID simId, String fromStatus, String toStatus, UUID outboxId,
                                    String action, Instant stateSince, int reconcileCount,
                                    String reason, String outcome) {
        long stateAgeSeconds = stateSince != null ? Duration.between(stateSince, Instant.now()).getSeconds() : 0;
        log.info("simulation.reconcile: simulationId={} fromStatus={} toStatus={} outboxId={} action={} stateAgeSeconds={} reconcileCount={} reason={} outcome={}",
                simId, fromStatus, toStatus, outboxId, action, stateAgeSeconds, reconcileCount, reason, outcome);
    }
}
