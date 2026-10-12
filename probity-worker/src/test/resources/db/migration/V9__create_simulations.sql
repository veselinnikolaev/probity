-- =============================================================================
-- V9__create_simulations.sql
-- =============================================================================
-- Monte Carlo simulation runs and their full GBM result payload.
--
-- Conventions (matching existing schema):
--   - TIMESTAMP (not TIMESTAMPTZ) — consistent with users/portfolios/positions
--   - UUID PRIMARY KEY (not DEFAULT gen_random_uuid() — Hibernate generates it)
--   - Soft-delete pattern (deleted / deleted_at) — matches BaseEntitySoftDelete
--   - Optimistic locking (version BIGINT) — matches all other aggregate roots
--   - NUMERIC(19,4) for monetary values — consistent with price_bars/positions
--   - ON DELETE CASCADE from portfolios — simulation history dies with portfolio
--
-- The heavy nested data (paths, percentile series, distribution) is stored as
-- JSONB in result_payload so the schema stays a single-row lookup per run.
-- =============================================================================

CREATE TABLE simulations
(
    id                      UUID            NOT NULL,
    portfolio_id            UUID            NOT NULL REFERENCES portfolios (id) ON DELETE CASCADE,
    user_id                 UUID            NOT NULL REFERENCES users (id),

    -- ── Request parameters (persisted for auditability / replay) ──────────
    number_of_simulations   INTEGER         NOT NULL,
    time_horizon_days       INTEGER         NOT NULL,
    confidence_level        DOUBLE PRECISION NOT NULL,
    assumed_return_pct      DOUBLE PRECISION,           -- NULL = derived from market data
    assumed_volatility_pct  DOUBLE PRECISION,           -- NULL = derived from market data

    -- ── Portfolio value snapshot at time of run ───────────────────────────
    current_portfolio_value NUMERIC(19, 4)  NOT NULL,

    -- ── Full result payload (JSONB) ───────────────────────────────────────
    -- Contains: statistics, outcomes, percentileSeries, allPaths, distribution
    result_payload          JSONB           NOT NULL,

    -- ── Audit (matches BaseEntitySoftDelete) ─────────────────────────────
    created_at              TIMESTAMP       NOT NULL,
    updated_at              TIMESTAMP,

    -- ── Soft-delete ───────────────────────────────────────────────────────
    deleted                 BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,

    -- ── Optimistic locking ────────────────────────────────────────────────
    version                 BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_simulations PRIMARY KEY (id)
);

-- =============================================================================
-- Indexes
-- =============================================================================

-- Primary query path: all simulations for a portfolio, newest first
CREATE INDEX idx_simulations_portfolio_id
    ON simulations (portfolio_id, created_at DESC)
    WHERE deleted = FALSE;

-- Access-control queries: simulations owned by a user
CREATE INDEX idx_simulations_user_id
    ON simulations (user_id)
    WHERE deleted = FALSE;

-- GIN index on JSONB payload — enables future JSON-path queries
-- (e.g. filter by statistics.expectedFinalValue) without full scans
CREATE INDEX idx_simulations_payload_gin
    ON simulations USING GIN (result_payload);

-- =============================================================================
-- Comments
-- =============================================================================

COMMENT ON TABLE  simulations
    IS 'Monte Carlo GBM simulation runs — one row per execution, result stored as JSONB';

COMMENT ON COLUMN simulations.result_payload
    IS 'JSONB: {statistics, outcomes, percentileSeries, allPaths, distribution}';

COMMENT ON COLUMN simulations.assumed_return_pct
    IS 'Caller-supplied annual return override (%); NULL = service derives from 90d market data';

COMMENT ON COLUMN simulations.assumed_volatility_pct
    IS 'Caller-supplied annual volatility override (%); NULL = service derives from 90d market data';

COMMENT ON COLUMN simulations.current_portfolio_value
    IS 'Live portfolio value (NUMERIC 19,4) fetched from market data at time of simulation run';