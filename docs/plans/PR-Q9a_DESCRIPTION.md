# PR-Q9a — result-payload storage split, API contract unchanged (Q9 §10)

Implements **PR-Q9a** of `docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md` §10 under the §9
sign-off (**Option A**, owner-signed-off 2026-10-19). **PR-Q9b (API split) and PR-Q9c
(backfill/async) are not started.**

## What it does

- **Additive migration `V19__simulation_paths.sql`** (byte-identical in both migration
  dirs): `simulation_paths(simulation_id uuid PK, paths jsonb, created_at timestamptz)`.
  **No FK to `simulations`** — the loadtest harness `TRUNCATE TABLE simulations` would be
  rejected by one; path rows may therefore be orphaned by a reset, documented in the
  migration and here. Guarded, V18-style grants: `SELECT`/`UPDATE` on `simulations` plus
  the first **`INSERT` grant for `probity_worker`** on `simulation_paths`.
- **Split completion write** in `SimulationRepository.complete()`, same transaction:
  `result_payload` keeps summary + `percentileSeries` + `distribution` (≈ 11.6 KB stored)
  and `allPaths` goes to its own row (≈ 1.46 MB). New `findPaths(UUID)`.
- **Dual-source read** in the worker `SimulationMapper`: `allPaths` is reassembled from
  `simulation_paths` when the row has none inline, else from the inline payload — so
  **every endpoint's response is unchanged**, for new **and** existing rows. No backfill,
  no destructive migration.
- `SimulationPayload.withoutAllPaths()`; `ResponsePathSampler` with
  `SimulationProperties.responsePathsSampleSize = 0` (**default off ⇒ responses
  byte-identical**; machinery dormant for Q9b).

## Scope limits (deliberate)

- Sync `POST /run` `save()` still writes the full inline payload (**A1**); only
  `complete()` splits.
- Sampling default 0 (**A2**).
- No change to concurrency, partitions, lease, poll interval, park/seek, the consumer
  gate, HPA or `compose.yaml`.
- No merge in this PR.

## Verification

- `mvnw.cmd -o clean verify -B` → **exit 0**, **378 run / 1 skipped** (353 api + 25
  worker). Baseline 365 → delta is exactly the **13 new tests**; **0 pre-existing tests
  modified**.
- **New tests:** `ResponsePathSamplerTest` (6), `SimulationSplitWriteIntegrationTest` (4),
  `SimulationSplitReadIntegrationTest` (3).
- **Runtime check (compose stack):** V19 applied on boot; worker ran as `probity_worker`
  (never `postgres`); run `9718630d…` → `202` (accept 167 ms) → `COMPLETED` (e2e 3,620 ms),
  1 consumer / 6 partitions / lag 0; stored `result_payload` **11,656 B** with
  `allPaths: []` + `simulation_paths` row **1,460,056 B**; `GET /{id}/status` **3,901,292 B**
  and `GET /{id}` **3,901,056 B**, both 200; `dayIndex` total 91,637 = 1000×91 + 7×91.
- **§10 measurement gate** (P0 = master `df2d537` vs this branch; PR-D5 protocol; 1 warm-up
  + 3 Scenario A runs per condition; one session; Scenario B skipped as §10 requires):
  full numbers, method and limits in `docs/PHASE_4_MEASUREMENTS.md` → *Q9a verification*.

## Headline numbers (one dev laptop, shared with Postgres/Kafka; relative, not capacity)

| measure | P0 @1 rep | Q9a @1 rep | P0 @3 rep | Q9a @3 rep |
|---|---|---|---|---|
| throughput (sims/s) | 1.767 (1.658–1.779) | **2.670 (2.592–2.684)** | 3.161 (3.065–4.418) | **5.377 (4.867–5.594)** |
| e2e p95 / p99 (ms) | 4,599.5 / 4,876.5 | 2,959.9 / 3,319.1 | 3,251.6 / 3,994.2 | 1,581.7 / 2,001.9 |
| WAL/sim (MB, LSN bracket) | 23.819 | **1.539** | 20.918 | **1.528** |
| checkpoints_req (A1/A2/A3) | 3/2/3 | 0/1/0 | 3/1/3 | 0/0/1 |
| api / worker CPU (s per run) | 9.27 / 7.55 | 9.16 / 18.49 | 15.37 / 11.01 | 8.03 / 18.67 |
| status body (B) | 3,901,906 | 3,901,535 | 3,902,045 | 3,901,573 |
| stored payload / paths per sim (B) | 1,471,772 / – | 11,646 / 1,460,208 | 1,471,712 / – | 11,651 / 1,460,117 |

- **Throughput +51 % (1 rep) / +70 % (3 rep)**; ranges do not overlap. The recorded
  **≈ 10x (P1) hypothesis is refuted** — Q9a still writes every path byte, only to a second
  row, while P1 wrote none. The two controls now bound the completion write from both sides.
- **WAL/sim ≈ 15x lower** with requested checkpoints nearly disappearing. The same total
  bytes cost far less WAL — **why is not investigated** (consistent with, not proof of, the
  §9 row-rewrite-vs-bytes note).
- **Stored bytes: total unchanged** (payload + paths = P0's inline row within random-data
  variance) — the split loses nothing.
- **Contract kept:** status bodies inside the recorded ±~140 B band at both replica counts.
- **Worker CPU per 60-sim run rose** (7.55 → 18.49 s); mechanism **not investigated**.

## Rollback

1. **Revert this PR** (code): `complete()` returns to the single inline write,
   `SimulationMapper` reads inline only, `simulation_paths` becomes unread.
2. **Rows written under Q9a still work after revert only for inline reads — their
   `allPaths` remains in `simulation_paths` unreferenced** (same as §10): either re-apply
   Q9a, or run the Q9c backfill (move those rows' paths back inline) before abandoning the
   split. State: **revert + Q9c backfill, or accept the orphaned paths rows** — no data loss
   (the paths are still stored, just unread by reverted code).
3. `V19` is additive and stays; drop the table with a follow-up migration only if the
   design is abandoned (dropping it discards Q9a rows' offloaded paths).

## Notes

- Worker password: local role reset from `.env` (previous scram hash unrecoverable),
  verified over the **scram-sha-256 host path** — `compose.yaml:161` maps
  `DB_PASSWORD=${DB_PASSWORD}`; see `docs/AGENT_LESSONS.md` rule 22.
- Loadtest residue: 421 orphan `simulation_paths` rows were deleted after the gate; the
  last run's 60 sims + 60 paths rows were left (recorded protocol leaves the last run).
