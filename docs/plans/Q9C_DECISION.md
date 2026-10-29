# Q9c decision note

**Status: closed — inconclusive, not pursued**

**Scope:** design and measurement only; no Q9c implementation is included.

## Current state

Q9a moves `allPaths` out of `simulations.result_payload` into
`simulation_paths`, but the two writes remain synchronous and transactional.
Q9b serves a deterministic bounded sample from
`/api/v1/simulations/{id}/paths?n=N`. The completion path still waits for the
paths write before the Kafka listener acknowledges the record.

## Options

### A. Deferred paths write

Persist the summary and percentile data first, then write paths from a background
writer or completion event. `/paths` should return `202 Accepted` with
`Retry-After` while the row is `PENDING`, and the frontend should poll until
`READY`. Add `paths_status` with `PENDING`, `READY`, and `FAILED` so a worker
crash is visible. The UI should show “result ready, paths still processing” for
`PENDING` and a retryable error for `FAILED`.

The paths write must be idempotent by simulation ID, with bounded exponential
retry and a terminal `FAILED` state after the retry budget. This moves the write
off the consumer thread but does not prove a WAL reduction: the write still
happens, only later and under a different transaction boundary.

### B. Persist only a configurable sample

Persist `N` paths, default `100`, using the existing deterministic sampler:
global minimum and maximum paths plus evenly spaced indices. Compute statistics
and percentiles from all generated paths before sampling. The omitted `/paths`
default is capped to the stored count; an explicit value above the stored count
is rejected.

Existing rows retain their current 1000-path data and are not backfilled. Tests
must cover extrema preservation, deterministic selection, explicit oversize `n`,
omitted `n` on a short row, and old-row compatibility. Rollback is a code revert;
new sampled rows cannot provide omitted paths without a forward backfill or a
retention decision.

### C. Compress the paths representation

Keep the summary inline and store paths as compact binary or gzip data. Reads pay
decompression CPU and the mapper must handle the compressed representation.
Existing rows remain readable through the current dual-source path. This changes
storage representation but keeps a completion-time write; its throughput and WAL
effect are unmeasured.

## Measurement status

The earlier B figures in this note were **not valid evidence for this task**:
step 2 found no scratch branch, no stash, no experiment notes containing the
sample code, and the database had zero `simulations` and zero `simulation_paths`
rows. The stored path count could therefore not be verified.

A fresh proof run was started from the current `origin/master` build. The
`simulation_paths` schema was verified as `paths jsonb NOT NULL`, and the
approved `loadtest/seed.sql` was used because AAPL/GOOGL `price_bars` were empty.
The first one-simulation proof failed with HTTP 401. The second failed with the
same HTTP 401 after the throwaway password was aligned to the local seed
credential. Server logs show the exact cause: `Login attempt for identifier: DO`;
the temporary identifier file included the SQL client's leading `DO` output, so
the harness was not given the throwaway username. No row was created, so
`jsonb_array_length(paths) = 100` was never demonstrated.

Per the repository rule, the experiment stopped after the second failed command.
The four-condition table, WAL per simulation, stored bytes per row, status body
bytes, API/worker CPU, and Kafka lag for the corrected B experiment are
**not investigated**. The earlier approximately **17 simulations/s** no-payload
ceiling remains historical context only, not a B result.

## Known problems with the experiment

- Earlier sample-only figures are **unverifiable**: the stored paths fell only
  about **7%** although about **100 of 1000** were meant to be stored.
- WAL and status-body bytes were **not measured** for B.
- The final clean three-replica master control **never completed** (stale
  processes).
- The re-run **stopped after two 401s** — both were the **admin web login**
  (the harness logged in as `DO`), not the worker's `probity_worker` DB
  connection.
- The 1-replica master control of **2.60 sims/s** is below the **3.14** recorded
  after Q9b, so the sessions are **not comparable**.

**Old table — not valid evidence.** The earlier sample-only table
(1-replica master **2.598**, B **3.144**, 3-replica master **6.172**, B
**5.087** sims/s) is withdrawn; the stored-path counts could not be verified.
The placeholder table below is also not a result.

| condition | replicas | throughput | e2e p95/p99 | WAL/simulation | stored bytes | status bytes |
|---|---:|---|---|---|---|---|
| master | 1 | not measured | not measured | not measured | not measured | not measured |
| B, 100 paths | 1 | not measured | not measured | not measured | not measured | not measured |
| master | 3 | not measured | not measured | not measured | not measured | not measured |
| B, 100 paths | 3 | not measured | not measured | not measured | not measured | not measured |

The expected B storage comparison would be approximately ten times fewer
stored path elements (100 of 1000), but that expectation is **not a measured
result**. The three-replica limiter is also **not investigated**: API CPU,
worker CPU, Kafka lag, and Postgres were not captured in a valid paired block.

## Recommendation

**Inconclusive. Q9c is not pursued now. Q9a and Q9b delivered the large gains.**

The required proof that the build stored exactly 100 paths was never obtained,
and both proof commands failed authentication at the harness layer before
creating a simulation. Any retry must first show `jsonb_array_length(paths) = 100`
plus `pg_column_size` on one sample-only row, then run the four conditions in one
session with process assertions before and after each block.

Option A remains an owner-gated follow-up if completion latency still matters:
measure an idempotent deferred writer with `paths_status`, `202/Retry-After`,
and crash/retry tests. C should be evaluated only after measuring decompression
CPU and WAL.

## Before anyone retries

1. Reset the `probity_worker` password locally first (out of band; no secret in
   the repo).
2. Use the pinned `grafana/k6:v2.3.0` image, not `latest`.
3. Ensure no stale processes: no leftover Java workers and no `prd*`/`q9*`
   containers.
4. Check `jsonb_array_length(paths)` of the stored paths before measuring.

## Proposed implementation sequence if A is approved

1. Add `paths_status` and an idempotent paths-write command; test transitions and
   duplicate delivery.
2. Move the paths write behind a durable completion event/outbox; test delayed
   and unavailable writers.
3. Add `/paths` `202`/`Retry-After` behavior and frontend polling/error states.
4. Run the full P0/A protocol at one and three replicas, including WAL, stored
   bytes, status bytes, CPU, Kafka lag, and crash/retry recovery.
5. Roll back by disabling the deferred writer only after deciding how
   `PENDING`/`FAILED` paths are surfaced.

## Owner questions

- Is storing only 100 paths acceptable, knowing existing rows retain 1000 and
  rollback cannot recreate omitted paths?
- What is the maximum `n` the UI will ever request?
- Is a summary `COMPLETED` before paths are `READY` acceptable?
- Should failed path persistence be retryable indefinitely, bounded, or user-visible
  as a permanent partial result?

