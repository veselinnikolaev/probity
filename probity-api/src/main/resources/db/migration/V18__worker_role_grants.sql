-- =============================================================================
-- V18__worker_role_grants.sql
-- =============================================================================
-- Least-privilege grants for the dedicated worker DB role (Phase 4 section 2.2).
--
-- The worker:
--   * claims / completes / releases / marks-failed simulations  -> SELECT + UPDATE
--   * reads the portfolio/price schema while executing          -> SELECT
--   * reads flyway_schema_history                               -> SELECT (required
--     by ddl-auto=validate, which consults the history table at startup to detect
--     schema drift)
-- No INSERT / DELETE anywhere.
--
-- The ROLE itself is provisioned out of band (ops / a secret-managed provisioning
-- step); its password never appears in a migration. This migration is therefore
-- guarded and idempotent: if the role is absent the block is a no-op instead of a
-- failure that would block startup, and if it is present the grants re-apply
-- without error.
-- =============================================================================

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'probity_worker') THEN
        GRANT USAGE ON SCHEMA public TO probity_worker;

        -- The worker's write surface: claim/complete/release/mark-failed.
        GRANT SELECT ON simulations TO probity_worker;
        GRANT UPDATE ON simulations TO probity_worker;

        -- The read surface the execution path walks.
        GRANT SELECT ON portfolios           TO probity_worker;
        GRANT SELECT ON portfolio_positions  TO probity_worker;
        GRANT SELECT ON assets               TO probity_worker;
        GRANT SELECT ON price_bars           TO probity_worker;

        -- ddl-auto=validate consults the history table at startup.
        GRANT SELECT ON flyway_schema_history TO probity_worker;
    END IF;
END $$;