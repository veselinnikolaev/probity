-- =============================================================================
-- V21__simulation_reconciliation.sql
-- Additive support for the M2 reconciliation sweeper. No data rewrite.
-- =============================================================================

-- Global staleness scan: "all live rows of status X whose state is older than T".
-- The existing (user_id, status) index cannot serve a cross-user sweep.
CREATE INDEX IF NOT EXISTS idx_simulations_status_state_age
    ON simulations (status, COALESCE(updated_at, created_at))
    WHERE deleted = FALSE;

-- Reconciliation bookkeeping on the outbox row (one row per simulation).
ALTER TABLE simulation_outbox
    ADD COLUMN IF NOT EXISTS reconcile_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_reconciled_at TIMESTAMPTZ;

COMMENT ON COLUMN simulation_outbox.reconcile_count
    IS 'Number of times the sweeper reset this row to unpublished. Alerting/backoff only; never a delete threshold.';
COMMENT ON COLUMN simulation_outbox.last_reconciled_at
    IS 'Last sweeper reset. Drives the per-row cooldown so two sweepers cannot loop on one row.';

-- Grants: the sweeper only SELECTs and UPDATEs. It never INSERTs or DELETEs.
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'probity_worker') THEN
        GRANT SELECT, UPDATE ON simulation_outbox TO probity_worker;
        GRANT SELECT, UPDATE ON simulations       TO probity_worker;
    END IF;
END $$;
