-- =============================================================================
-- V17__make_simulation_result_nullable.sql
-- =============================================================================
-- Make result_payload nullable to support PENDING/PROCESSING status.
-- Simulations in PENDING or PROCESSING status don't have results yet.
-- =============================================================================

ALTER TABLE simulations
    ALTER COLUMN result_payload DROP NOT NULL;

-- Update comment to reflect nullable behavior
COMMENT ON COLUMN simulations.result_payload
    IS 'JSONB: {statistics, outcomes, percentileSeries, allPaths, distribution}. NULL for PENDING/PROCESSING simulations.';
