# Docs: write-path investigation (WAL config, writes per simulation)

Docs-only follow-up (not a Phase 4 PR-D6 — Phase 4 §2.9 ends at PR-D5). No `src`,
test, `pom.xml`, migration or `compose.yaml` change. The only Postgres changes were
**local dev DB config**, recorded first and restored at the end (confirmed with `SHOW`).

## What this answers

- **Q9's precondition — "how is the payload written?"** The static and runtime proof that
  **exactly one statement writes `result_payload`** per simulation.
- **Whether the local WAL knobs PR-D5 listed** (`max_wal_size` + `checkpoint_timeout`,
  `wal_compression`, and a labeled `synchronous_commit=off` diagnostic) move WAL and
  throughput at 1 and 3 worker replicas — before any payload-storage decision.

New section in `docs/PHASE_4_MEASUREMENTS.md`: *Write-path investigation - writes per
simulation and WAL under local Postgres config (2026-10-15)*. A short entry in
`docs/TECHNICAL_REPORT.md`, and one pointer line under Q9 in
`docs/plans/PHASE_4_WORKER_EXTRACTION.md`.

## Headline numbers (one dev laptop: 24 cores / 32 logical, 31.6 GB RAM, shared with Postgres/Kafka/Redis; relative, not capacity)

**Writes per simulation — exactly 1 statement writes `result_payload`.**

- Static: the `completeIfClaimHeld` completion `UPDATE` in `SimulationRepository.complete`
  is the sole payload writer. The column is `updatable = false` (so JPA dirty-checking never
  writes it), the claim path (`claimForProcessing`) writes only `status`/`updated_at`, the
  API's PENDING insert writes `result_payload` as `NULL`, and `runAsync` is **not**
  `@Transactional`, so there is no payload flush.
- Runtime (`log_statement = 'mod'`, one `run-async`): **1** `insert into simulations`, **2**
  `update simulations` (claim, then completion), and the string `result_payload` in exactly
  **1** statement — the completion `UPDATE`.

## Standalone — one simulation (1 replica, clean dedicated pass)

| condition | WAL, LSN-diff (B) | `pg_stat_wal.wal_bytes` (B) | `wal_fpi` |
|---|---|---|---|
| c0 | 4,514,576 | 4,506,404 | 4 |
| c1 | 4,506,080 | 4,510,826 | 1 |
| c2 | 4,506,776 | 4,492,133 | 0 |
| c3 | 4,506,464 | 4,503,114 | 1 |
| cD | 4,505,712 | 4,491,069 | 0 |

One completion writes **≈ 4.51 MB in every condition** (matches PR-D5's 4,506,280 B); the
two methods agree within **0.4 %**, full-page share ≈ 0. None of the knobs move the
standalone write — it is the row/TOAST rewrite, not checkpoint-driven.

## Scenario A — 60 simulations (WAL per simulation = LSN-diff total / 60)

Median (min–max) over 3 runs. **1 replica:**

| condition | WAL/sim (MB) | full-page share | `checkpoints_req` (3 runs) | throughput (sims/s) |
|---|---|---|---|---|
| c0 | **24.61** (22.85–25.91) | 0.77 | 8 | **1.896** (1.845–1.942) |
| c1 | 20.46 (19.11–21.06) | 0.65 | 2 | 1.840 (1.711–1.881) |
| c2 | 17.79 (16.72–17.93) | 0.93 | 6 | 1.598 (1.566–1.721) |
| c3 | 16.16 (16.13–17.90) | 0.70 | 1 | 1.645 (1.591–1.660) |
| cD | 16.16 (16.13–17.03) | 0.71 | 1 | 1.746 (1.701–1.775) |

**3 replicas:**

| condition | WAL/sim (MB) | full-page share | `checkpoints_req` (3 runs) | throughput (sims/s) |
|---|---|---|---|---|
| c0 | **22.23** (19.85–24.96) | 0.72 | 7 | **3.276** (3.274–3.512) |
| c1 | 19.14 (15.65–21.46) | 0.66 | 2 | 3.251 (2.901–3.909) |
| c2 | 17.12 (9.97–17.56) | 0.87 | 5 | 2.753 (2.687–4.057) |
| c3 | 15.94 (9.44–16.77) | 0.70 | 1 | 2.892 (2.590–3.783) |
| cD | 16.16 (16.14–17.51) | 0.70 | 1 | 2.633 (2.441–2.828) |

- **Conditions:** c0 = recorded default (`max_wal_size=1024 MB`, `checkpoint_timeout=300 s`,
  `wal_compression=off`, `synchronous_commit=on`); c1 = `4096 MB`/`900 s`; c2 =
  `wal_compression=pglz`; c3 = both; cD = c3 + `synchronous_commit=off`, labelled
  **"diagnostic only, not a recommendation, durability reduced"**.
- Under load, WAL per simulation is **3.5x–5.5x** the standalone write and **≈ 11x–17x** the
  1.47 MB stored payload (c0 = 16.7x at 1 replica, 15.1x at 3, matching PR-D5's 22.75 MB /
  15x). Full-page images are **60–93 %** of it.
- Both knobs cut WAL per simulation vs c0 medians: `max_wal_size` + `checkpoint_timeout`
  **-17 % / -14 %** (1/3 rep); `wal_compression=pglz` **-28 % / -23 %**; both **-34 %/-28 %**.
  `checkpoints_req` falls from 7–8 to 1–2 per 3 runs; `checkpoints_timed` was **0** everywhere.
- **Throughput did not improve.** No condition beats c0, and where a gap exceeds the 3-run
  spread it is *lower* (c2 and cD at 1 replica; at 3 replicas the spreads overlap). Cutting
  WAL bytes did not raise throughput here.
- **cD** (`synchronous_commit=off`, durability reduced) showed no win outside the spread at 3
  replicas and only a marginal one at 1. It is **not a recommendation** and was not left on.

## Verification

- `mvnw.cmd -o clean verify -B` → exit 0, BUILD SUCCESS (3:27), **365 run, 1 skipped**
  (350 api + 15 worker) — unchanged (docs-only).
- `probity-worker:prd5` at 1 and 3 replicas against the compose stack; per condition **1
  warm-up + 3 Scenario A (6 VUs x 60)** runs with `TRUNCATE TABLE simulations` between runs,
  `/actuator/health` UP, observability stopped.
- Postgres GUCs changed **local to this dev DB only**, then restored (`ALTER SYSTEM RESET` +
  `pg_reload_conf()`); `SHOW` confirms 1024 MB / 300 s / `wal_compression=off` /
  `synchronous_commit=on`, and `postgresql.auto.conf` holds no experiment setting.
- Every process started for the test stopped (API by real :8080 PID, workers by
  `docker rm -f`); compose `kafka`/`postgres`/`redis` left as they were.

## Method notes worth keeping

- `wal_compression` and `synchronous_commit` apply to **new sessions only**, so the worker
  replicas were restarted after each `ALTER SYSTEM` + `pg_reload_conf()` to force pooled
  Hikari connections to pick them up (`max_wal_size`/`checkpoint_timeout` are `sighup`,
  live).
- `pg_stat_wal` lags multi-second. Bracket a before/after snapshot around **only** the
  measured operation: an earlier pass read it while a preceding bulk run's checkpoint was
  still flushing and attributed up to 21 MB to a 4.5 MB simulation. The LSN-diff figure
  (`pg_current_wal_insert_lsn()` + `pg_wal_lsn_diff()`) was correct throughout and is the
  authoritative number here; the counter is a cross-check (they agree within ≈ 3 % on
  Scenario A, 0.4 % standalone). Recorded as AGENT_LESSONS #20.

## Follow-ups (recorded, not fixed here)

1. **Q9 payload storage** stays open — this only supplies the write-path precondition (one
   payload write per simulation) it needs; no storage decision is made.
2. **Where throughput actually binds** at 1/3 replicas is **not investigated**. WAL volume
   alone is not the constraint, since cutting it 14–34 % did not raise throughput.
3. **Compression cost.** The equal-or-lower throughput under `pglz` (a possible
   compression/CPU effect) is **not investigated**.
4. **Local-config only.** These GUCs and this laptop are not a capacity claim; no HPA
   (Q4), concurrency, partition or code change is implied.
