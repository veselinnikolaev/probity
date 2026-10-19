-- =============================================================================
-- V19__simulation_paths.sql
-- =============================================================================
-- Q9a storage split (docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §9/§10, owner-signed
-- Option A): the byte-heavy allPaths array leaves simulations.result_payload and
-- lands in this table, written in the SAME transaction as the fenced completion
-- UPDATE (SimulationRepository.complete).
--
-- Additive only: simulations is untouched, nothing is backfilled, no existing row
-- changes. Old rows keep their inline allPaths and the dual-source read
-- (SimulationMapper.toDto in the worker) serves them unchanged — this table starts
-- empty and is only written by new completions.
--
-- Deliberately NO foreign key to simulations(id), which §10's table spec mirrors:
-- the recorded load-test reset (loadtest/run-baseline.ps1) runs
-- `TRUNCATE TABLE simulations;` and Postgres refuses to truncate a table an FK
-- references unless the referencing table is truncated in the same statement or
-- CASCADE is used — neither is true for that harness. Orphaned paths rows can
-- therefore only come from hard deletes (load-test reset, test cleanup);
-- production only soft-deletes (deleted flag), so no orphan can appear there.
--
-- `paths` holds exactly what result_payload->'allPaths' used to hold (same records,
-- same Jackson serialisation), so a reassembled response is byte-identical to a
-- pre-Q9a response.
--
-- Conventions (matching V9/V11):
--   - UUID PRIMARY KEY assigned by Hibernate, like simulations.id
--   - TIMESTAMPTZ for Java Instant, the post-V11 convention
-- =============================================================================

CREATE TABLE IF NOT EXISTS simulation_paths
(
    simulation_id UUID         NOT NULL,
    paths         JSONB        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_simulation_paths PRIMARY KEY (simulation_id)
);

COMMENT ON TABLE  simulation_paths
    IS 'Q9a off-row allPaths store — one row per split-written simulation (Option A)';

COMMENT ON COLUMN simulation_paths.paths
    IS 'JSONB array of {pathId, values:[{dayIndex,value}]} — exactly what result_payload->''allPaths'' held before Q9a';

COMMENT ON COLUMN simulation_paths.simulation_id
    IS 'FK-shaped key to simulations(id), intentionally NOT a FK — see header note (TRUNCATE harness)';

-- =============================================================================
-- Grants — guarded and idempotent in the style of V18
-- =============================================================================
-- The role is provisioned out of band (V18 header); a missing role is a no-op
-- instead of a startup failure, present it re-applies without error.
--
-- This is the first INSERT grant in the Phase 4 least-privilege set (V18 gave the
-- worker SELECT + UPDATE on simulations only, "No INSERT / DELETE anywhere"):
-- after the split the worker writes the paths row inside the completion
-- transaction, and it also reads paths back — SimulationMapper reassembly runs in
-- the worker process for the run-async DTO. The API process serves HTTP reads as
-- the migration owner (V19's creator), exactly like it reads simulations today.

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'probity_worker') THEN
        GRANT SELECT, INSERT ON simulation_paths TO probity_worker;
    END IF;
END $$;
