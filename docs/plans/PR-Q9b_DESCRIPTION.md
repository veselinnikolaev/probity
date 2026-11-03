# PR-Q9b — API split: small status/detail/list + new sampled paths endpoint (Q9 §10)

Implements **PR-Q9b** of `docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md` §10 under the §9
sign-off (**Option A**, owner-signed-off 2026-10-19). PR-Q9a (storage split) is merged;
**PR-Q9c (backfill/async) is still not started.**

## ⚠️ Breaking API change (the point of this PR)

`GET /api/v1/simulations/{id}`, `GET /api/v1/simulations` (list) and `POST /api/v1/simulations/run`
(and `run-async`… see note) no longer return `allPaths`. They return the summary,
percentile series, distribution and statistics exactly as before; only `allPaths` is gone.
The paths are served by the new endpoint:

**`GET /api/v1/simulations/{id}/paths?n=N`** →
`{ "id": …, "totalPaths": <all stored>, "sampleSize": <served>, "paths": [ … ] }`

- `n` optional; **default 100** via `simulation.response.paths-sample-size`
  (env `SIMULATION_RESPONSE_PATHS_SAMPLE_SIZE`); **max 1000** via
  `simulation.response.paths-max-sample-size` (env `SIMULATION_RESPONSE_PATHS_MAX_SAMPLE_SIZE`).
- `n` validated: `< 1`, `> max`, or an **explicit** `n > totalPaths` → **400**
  (`InvalidPathsRequestException`, message preserved by `GlobalExceptionHandler`); an
  **omitted** `n` is the configured default and is served **capped to the stored count**;
  a row with **zero** stored paths → **200** with an empty sample (not 404).
- **Same auth and same ownership guard as `GET /{id}`** (unknown id or wrong owner → 404,
  no auth → 401).
- `Cache-Control: private, no-store`; no ETag.

**Sampling is deterministic and reproducible** — evenly spaced indices over the stored path
order (no seed), **plus the path holding the global min and the path holding the global max
value** across all time steps, so the fan is never clipped at the extreme. Percentiles and
statistics are still computed from **all** paths, not the sample. `N = 100` is an
**unmeasured first guess** (the owner's "at most ~N"); it is not a measured optimum.

## What it does — backend

- `ApiRoutes.Simulations.PATHS`; new API DTO `SimulationPathsResponse`.
- `allPaths` removed from the API `SimulationResponse`, worker `SimulationData` and
  `SimulationStatusResponse.SimulationResponseResult`; unused `SimulationResponseAllPath` +
  `mapPath` removed (`SimulationResponsePathPoint` kept for the percentile series).
- `SimulationMapper.toDto` no longer carries paths; new `storedPaths(Simulation)` is the Q9a
  **dual-source read** (inline payload **or** `findPaths`) reused by the paths path.
- `MonteCarloSimulationService.getPaths(id, userId, n)` applies `ResponsePathSampler` and
  returns `SimulationPort.SampledPaths`; `SimulationApplicationService.getSimulationPaths`
  implements the port method.
- `SimulationController.getPaths` + `GlobalExceptionHandler` 400 mapping.
- `responsePathsSampleSize` default `0 → 100`; new `responsePathsMaxSampleSize = 1000`; both
  `probity-api` and `probity-worker` `application.yaml` updated.

## What it does — frontend

- `SimulationResult` drops `allPaths`; new `SampledPaths` type;
  `fetchSimulationPaths(id, n = 100)` + `DEFAULT_PATHS_SAMPLE_SIZE`.
- `FanChart` prop `allPaths` → `paths`; new exported `computeFanChartRange(paths,
  percentileSeries, currentValue)` computes the y-range from the **union** of the percentile
  series and the sampled paths (min/max included) so an extreme path is never clipped.
- MonteCarlo requests `/paths?n=100` **after** completion and renders the summary/bands
  first, adding the paths when they arrive (`pathsLoading` caption).

## Scope limits (deliberate)

- **No merge.**
- Other recorded API-drift items untouched: the async flow,
  CSRF on the assistant call, input-range validation, dead functions. `GET /auth/check-email` removed in M4c.
- No change to concurrency, partitions, lease/poll, park/seek, the consumer gate, HPA or
  `compose.yaml`.
- **PR-Q9c not started** (backfill, async offload, drop-table).

## Verification

- **Backend:** `mvnw.cmd -o clean verify -B` → see *Verified* note below (CI is the
  authority if the local run cannot complete).
- **Frontend:** `npm ci`, `npm run build` = 0, `npm test` = 0.
- **Tests added:** `SimulationPathsEndpointIntegrationTest` (10) — `n` bounds, explicit
  `n > stored` → 400, omitted `n` capped to the stored count, byte-identical
  reproducibility, min/max inclusion, default 100 when `n` omitted, both storage shapes,
  empty-paths row → 200, ownership → 404, no auth → 401, unknown → 404;
  frontend `FanChart.test.tsx` (5).
- **Test count:** 388 run / 1 skipped (**353 → 363 api, +10**; worker unchanged at 25) —
  baseline was 378 run / 1 skipped (353 api + 25 worker). Delta = **+10**, all new API
  tests; the three modified test classes changed assertions only, not test counts.
- **Tests modified (contract change forced):** `SimulationSplitReadIntegrationTest`,
  `SimulationIntegrationTest` (API) and `SimulationSplitWriteIntegrationTest` (worker) —
  list/status/detail assertions moved from `allPaths` to the paths endpoint / `doesNotExist`.
- **Runtime (measured, 1000 sims × 90 days, API host jar + `--network host` worker as
  `probity_worker`):** `run-async` → 202 (accept 395 ms) → `COMPLETED` (2,050 ms, 5 polls);
  `allPaths` absent from detail and status; `/paths?n=100` → 100/1000 paths, 100 distinct
  pathIds, **byte-identical** across two identical calls; sample min/max **equal** to the
  DB-wide min/max across all 1000 stored paths (143.085988884062, 8005.17174204478);
  `n=0`/`1001`/`2000` → 400 (`n must be between 1 and 1000`), omitted `n` → 200,
  unknown id → 404, no auth → 401; `Cache-Control: no-store, private`, no ETag.
  Body sizes: status **30,913 B** (was 3,901,906 B), detail **30,677 B**, list **30,679 B**,
  `/paths?n=100` **387,006 B**; stored `result_payload` 11,684 B + `simulation_paths`
  1,459,904 B (same ~1.47 MB total as Q9a).
- **Worker credential:** the out-of-band `probity_worker` password no longer matched the
  local dev role, so it was reset for this run (kept outside the repo); grants re-verified
  over the real `scram-sha-256` host path, negative tests still denied (`docs/AGENT_LESSONS.md`
  lesson 26).

## Headline numbers (one dev laptop, shared with Postgres/Kafka; relative, not capacity)

| measure | P0 (master) @1 rep | Q9b @1 rep | P0 @3 rep | Q9b @3 rep |
|---|---|---|---|---|
| throughput (sims/s, median [min–max]) | 2.212 [2.134–2.232] | **3.135** [3.130–3.172] | 4.754 [4.725–4.880] | **6.410** [6.380–6.533] |
| e2e p95 / p99 (ms) | 3,653.8 / 4,239.7 | 2,486.1 / 2,934.3 | 1,994.4 / 2,319.6 | 1,460.0 / 1,744.2 |
| accept p95 / p99 (ms) | 45.1 / 56.2 | 21.0 / 22.8 | 25.0 / 27.4 | 22.0 / 23.0 |
| WAL/sim (MB, LSN bracket) | 1.528 [1.528–1.563] | 1.551 [1.528–1.551] | 1.530 [1.529–1.553] | 1.559 [1.529–1.648] |
| status body (B) | 3,901,679 | **30,948** | 3,901,379 | **30,978** |
| detail body (B) | 3,901,443 | 30,712 | 3,901,143 | 30,742 |
| portfolio list body (B, ≈60 rows) | 234,087,457 | 1,841,051 | 234,093,098 | 1,841,289 |
| `/paths?n=100` body (B) | n/a (404) | 387,300 | n/a (404) | 386,919 |
| stored payload / paths per sim (B) | 11,642 + 1,460,539 | 11,639 + 1,460,517 | 11,649 + 1,460,472 | 11,644 + 1,460,517 |
| api / worker CPU per 60-sim run (s) | 11.14 / 18.42 | 3.19 / 9.81 | 8.94 / 21.59 | 2.84 / 11.17 |

- **Throughput** rises **2.212 → 3.135 sims/s** (1 rep) and **4.754 → 6.410** (3 rep) with
  **non-overlapping ranges** in both pairs — a measurement, not a promise.
- **Status/detail/list bodies fall from ≈ 3.90 MB to tens of KB.** Per-row list body is the
  largest aggregate change (≈ 234 MB → ≈ 1.8 MB for the same ≈ 60 rows).
- **WAL per simulation is flat** and reported as **not distinguishable** (1.528 → 1.551 at
  1 rep; 1.530 → 1.559 at 3 rep) — Q9b touches only the read/DTO side, so the completion
  write (Q9a's split) is unchanged. A result inside the run spread is "not distinguishable".
- The api CPU drop tracks serializing ~30 KB instead of ~3.9 MB per status poll; the
  worker CPU drop is measured but its cause is **not investigated**.

## Rollback

Revert this PR — responses return to the Q9a reassembled-full contract (dual-source read),
no schema change involved. Q9a's `simulation_paths` split write stays; the paths endpoint
disappears with the code.

## Notes

- `N = 100` is an unmeasured first guess, stated as such.
- Percentiles/statistics remain full-population; only the drawn sample is bounded.
