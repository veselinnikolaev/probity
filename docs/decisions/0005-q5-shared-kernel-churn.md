# ADR 0005 — Q5: shared-kernel churn (PR-D1)

**Status:** resolved — git mv only (PR-D1).

## Context

PR-D1 extracted the worker into a separate module. The only changes were package/import lines forced by types moving into `probity-shared`. The tests were `git mv`-ed into the new module structure, not rewritten.

## Decision

PR-D1 is a **git mv only** operation. Approving PR-D1 is **not** approval to modify test behavior or to break the "tests stay green and unmodified" constraint (PHASE_4_WORKER_EXTRACTION.md lines 258-259). The test files were moved in parallel with the code move; no logical decision about data access, scaling, or design was recorded because the phase was purely mechanical.

## Consequences

- Tests follow the module move: `git mv` of test source files into the new module paths.
- No test behavior changes are implied by the PR merge; the "tests stay green" constraint is preserved.
- Any future enrichment of the worker's payload or data access must be a separate PR (Q5 fast-follow), not bundled into PR-D1.

## Evidence

- PHASE_4_WORKER_EXTRACTION.md lines 258-259: "Q5 - shared-kernel churn (PR-D1): OPEN. The extraction is a path move only - tests are `git mv`-ed, not rewritten."
- `git log --oneline` shows PR-D1 commit message references only package/import line changes.
- No test modifications (no `mvnw test` breakage) were needed when tests were moved.

[Link: PHASE_4_WORKER_EXTRACTION.md lines 258-259]