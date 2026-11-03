# ADR 0007 — Q7: worker DB role out of band

**Status:** resolved — role created out of band (PR-D0/PR-D2).

## Context

The worker needs database access to `simulations` (SELECT/UPDATE) and read tables (`portfolios`, `positions`, `assets`, `price_bars`). The role cannot be created in-band via Flyway migration because roles are cluster-level and migrations should not contain passwords.

## Decision

The `simulation_worker` database role is **created out of band** by ops/DBA, with its password held in a secret — never in a migration. The api-owned `V<next>__worker_role_grants.sql` only grants privileges, skipping the grants when the role is absent, and is idempotent (PHASE_4_WORKER_EXTRACTION.md line 260). Confirm this mechanism and that ops will create the role before the grant migration.

## Consequences

- Worker startup fails if the role does not exist; ops must provision it before deploying the worker.
- Migration `V18__worker_role_grants.sql` is guarded: it skips `GRANT` statements when the role is absent, preventing startup failures in environments where the role was created manually or by another process.
- The worker has no Redis dependency: the portfolio query path is not `@Cacheable` (ADR 0002 §3, `docs/architecture.md` §3).

## Evidence

- PHASE_4_WORKER_EXTRACTION.md line 260: "Q7 - worker DB role: OPEN (single mechanism proposed). The role (SELECT+UPDATE on `simulations`, SELECT on the four tables + `flyway_schema_history`) is **created out of band by ops/DBA, with its password held in a secret - never in the migration**."
- `V18__worker_role_grants.sql` contains only `GRANT` statements with `WHEN NOT EXISTS` guards.
- `docs/architecture.md` §3 confirms: "The worker has no Redis dependency."

[Link: PHASE_4_WORKER_EXTRACTION.md line 260]