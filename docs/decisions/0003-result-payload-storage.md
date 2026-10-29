# ADR 0003 — Split the payload: summary in `result_payload`, paths in `simulation_paths`

**Status:** implemented (PR-Q9a merged, 2026-10-19).

## Context

The baseline `POST /simulations/run` persisted a full Monte Carlo result payload
(4.27 MB raw, 1.47 MB stored jsonb, 7.89 MB per-row including TOAST/indexes) into
`simulations.result_payload`. This drove WAL amplification of ≈3.06x stored payload per
simulation and ≈15x under load, and capped single-replica throughput at ~1.767 sims/s.
The product owner confirmed the frontend only needs sampled paths + percentile bands
(FigChart draws from `allPaths` at default 10 000 but only needs summary + bands).

## Decision

Move path data out of `result_payload` into a new `simulation_paths` table, keeping
only summary + percentiles inline.

- **Q9a — storage split:** `result_payload` keeps ~27.7–30.4 KB (summary + percentiles),
  all 1000 paths go into `simulation_paths` (≈ 1.46 MB per simulation) via a new
  `V19__simulation_paths.sql` Flyway migration. The completion write stays in one
  transaction; the API contract is unchanged — consumers get the reassembled `allPaths`
  from dual-source reads.
- **Q9b — API split:** `GET /{id}/status`, `GET /{id}`, and the list endpoint drop
  `allPaths`. A new `GET /api/v1/simulations/{id}/paths?n=N` returns an evenly spaced
  sample (default n=100, global min + max included) plus `sampleSize=100` of
  `totalPaths=1000`  → 387,300 B at 1 replica / 386,919 B at 3 replicas. WAL per
  simulation drops to **1.539 MB at 1 replica** and **1.528 MB at 3** (−93.5 % / −92.7 %).
  Status bodies stay the same size (3,901,535 / 3,901,573 B vs P0's 3,901,906 / 3,902,045 B).
- **Q9c — not pursued:** deferred paths write (Option A in Q9c note), configurable sample
  (Option B), and compressed paths (Option C) were all deemed unlikely to improve
  throughput; the write-path cost is in the row rewrite pattern, not WAL volume alone.
- **Evidence:** `docs/PHASE_4_MEASUREMENTS.md` §7–8 (Q9a throughput +51 %/+70 % at 1/3
  replicas, WAL −93.5 %/−92.7 %, stored bytes unchanged, status contract kept; Q9b
  throughput +42 %/+35 %, status body 3,901,679 → 30,948 B ≈126× smaller, WAL flat,
  API CPU fell 11.14 → 3.19 s at 1 replica); `docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md`
  §9 decision (signed off Option A, 2026-10-19); `docs/plans/Q9C_DECISION.md` §closed
  (inconclusive, not pursued).