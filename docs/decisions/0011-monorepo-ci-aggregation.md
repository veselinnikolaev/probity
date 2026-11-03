# ADR 0011 — Monorepo and CI aggregation check

**Status:** resolved — monorepo layout with aggregated CI check (repository layout).

## Context

This repository is a monorepo combining the Java/Spring Boot backend, React/Vite frontend, and shared configurations in a single git repo. The CI workflow (`/.github/workflows/ci_cd.yaml`) aggregates jobs across monorepo paths: `Detect changes` → `Build & Test` (Maven on JDK 21, backend paths) and `Frontend Build & Test` (Node, `frontend/**`), with a required `CI Status` check that covers both.

## Decision

The monorepo layout is the canonical structure for this project. CI aggregation is configured as follows:
- `.github/workflows/ci_cd.yaml` has one workflow with path filters: backend changes trigger `Build & Test`; frontend changes trigger `Frontend Build & Test`; both feed into the required `CI Status` check.
- `CI Status` aggregates both build/test jobs; Docker push remains limited to pushes on `master`.
- No separate `frontend-ci.yml` exists — a subtree import would need to move workflows to the root `.github/workflows/`.

## Consequences

- Single repo simplifies cross-cutting changes (e.g., Dockerfile, dependency upgrades) — one PR changes everything.
- CI must have path filters to avoid running unnecessary jobs; `changes` job filters by backend paths, `frontend_test` filters by `frontend/**`.
- The required check `CI Status` protects both domains; a failure in either backend or frontend blocks merge.
- Moving frontend CI to a separate repo would require syncing versions and losing the aggregated `CI Status` check.

## Evidence

- `.github/workflows/ci_cd.yaml` defines the aggregated CI pipeline with monorepo path filters.
- `README.md` line 727: "CI Status aggregates changes, test, frontend-test (required when protected)."
- `README.md` line 728: "`ci_cd.yaml` runs the backend and frontend jobs from one workflow; each job is gated by its monorepo path filter."
- `frontend/.nvmrc` pins Node 20.20.1; `package.json` name is `@figma/my-make-file`.

[Link: .github/workflows/ci_cd.yaml; Link: README.md lines 727-728]