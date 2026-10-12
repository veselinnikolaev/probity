-- =============================================================================
-- V11__migrate_timestamps_to_timestamptz.sql
-- =============================================================================
-- Converts all TIMESTAMP columns to TIMESTAMP WITH TIME ZONE (TIMESTAMPTZ).
--
-- Why: Java Instant maps to TIMESTAMPTZ in Postgres. Plain TIMESTAMP has no
-- timezone information — comparisons across JVM timezone changes are unreliable.
-- TIMESTAMPTZ stores UTC internally and is unambiguous regardless of JVM/server
-- locale.
--
-- Data safety: existing data is interpreted as UTC (USING col AT TIME ZONE 'UTC').
-- If your DB was already running in UTC (Docker/Render default), no values change.
-- =============================================================================

-- ── users ────────────────────────────────────────────────────────────────────
ALTER TABLE users
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

-- ── assets ───────────────────────────────────────────────────────────────────
ALTER TABLE assets
    ALTER COLUMN created_at  TYPE TIMESTAMPTZ USING created_at  AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at  TYPE TIMESTAMPTZ USING updated_at  AT TIME ZONE 'UTC',
    ALTER COLUMN archived_at TYPE TIMESTAMPTZ USING archived_at AT TIME ZONE 'UTC';

-- ── portfolios ───────────────────────────────────────────────────────────────
ALTER TABLE portfolios
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

-- ── portfolio_positions ──────────────────────────────────────────────────────
ALTER TABLE portfolio_positions
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

-- ── price_bars ───────────────────────────────────────────────────────────────
ALTER TABLE price_bars
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

-- ── simulations ──────────────────────────────────────────────────────────────
ALTER TABLE simulations
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

-- ── user_preferences ────────────────────────────────────────────────────────
-- This table was created in V9 with plain TIMESTAMP defaults — fix those too.
ALTER TABLE user_preferences
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- Update the column defaults on user_preferences to be timezone-aware
ALTER TABLE user_preferences
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET DEFAULT now();
-- now() in Postgres always returns TIMESTAMPTZ — the defaults were already
-- correct semantically, but the column type change makes them consistent.