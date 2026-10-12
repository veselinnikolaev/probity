-- =============================================================================
-- V16__add_simulation_status.sql
-- =============================================================================
-- Add status column to simulations table for async execution tracking.
-- Status values: PENDING, PROCESSING, COMPLETED, FAILED
-- =============================================================================

ALTER TABLE simulations
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

-- Index for status-based queries (e.g., find stalled PROCESSING simulations)
CREATE INDEX idx_simulations_user_id_status
    ON simulations (user_id, status)
    WHERE deleted = FALSE;

-- Comment
COMMENT ON COLUMN simulations.status
    IS 'Execution status: PENDING (created, not yet picked up), PROCESSING (worker active), COMPLETED (success), FAILED (error)';