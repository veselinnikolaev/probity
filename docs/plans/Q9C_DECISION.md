# Q9c decision note

**Status: proposed, awaiting owner sign-off**  
**Scope:** design and measurement only; no Q9c implementation is included.

## Current state

Q9a already moves `allPaths` out of `simulations.result_payload` into
`simulation_paths`, but the two writes remain synchronous and transactional. Q9b
serves a deterministic bounded sample from `/api/v1/simulations/{id}/paths?n=N`.
The current completion path therefore still waits for the full paths write before
the Kafka listener acknowledges the record.

## Options

### A. Deferred paths write

Persist the summary and percentile data first, then write paths from a background
writer or completion event. `/paths` should return `202 Accepted` with
`Retry-After` while the row is `PENDING`, and the frontend should poll until
`READY`. Add a `paths_status` state (`PENDING`, `READY`, `FAILED`) so a worker
crash between the two steps is visible rather than silently appearing as a
successful result; the UI should show “result ready, paths still processing” for
`PENDING` and a retryable error for `FAILED`.

The paths write must be idempotent by simulation ID, with bounded exponential
retry and a terminal `FAILED` state after the retry budget. This moves the path
write off the consumer thread but does not prove that it reduces WAL: the write
still happens, only later and under a different transaction boundary. It also
allows a `COMPLETED` summary to exist without immediately available paths.

### B. Persist only a configurable sample

Persist `N` paths, default `100`, using the existing deterministic sampler rule:
global minimum and maximum paths plus evenly spaced indices. Compute statistics
and percentiles from all generated paths before sampling. `/paths?n=` must reject
an explicit value above the stored count; the omitted default is capped to the
stored count. The maximum useful `n` becomes the stored count.

Existing rows retain their current 1000-path data and are not backfilled. Tests
must cover minimum/maximum preservation, deterministic selection, explicit
oversize `n`, omitted `n` on a short row, and old-row read compatibility.
Rollback is a code revert; newly written sampled rows cannot provide the omitted
paths, so a rollback that requires full paths needs a forward backfill or a
retention decision.

### C. Compress the paths representation

Keep the summary inline and store paths as a compact binary or gzip value.
Reads pay decompression CPU and the mapper must handle the compressed
representation, while existing rows remain readable through the current
dual-source path. This reduces storage bytes but keeps a completion-time write
and does not establish a throughput gain; the write/rewrite cost remains
unmeasured.

## Measurement

**Method.** Local throwaway build only; no source change was committed. The
protocol used `loadtest/run-baseline.ps1`, `grafana/k6:latest` because the pinned
`grafana/k6:v2.3.0` image was unavailable, one warm-up plus three measured runs,
Scenario A (6 VUs × 60), observability containers stopped, and table reset
between runs. The worker authenticated as `probity_worker`; the initial attempt
was blocked by an empty local `price_bars` table, so only local throwaway AAPL and
GOOGL bars were added and removed afterward.

| condition | replicas | throughput median (min–max), sims/s | e2e p95 median (min–max), ms | e2e p99 median (min–max), ms |
|---|---:|---:|---:|---:|
| master control | 1 | 2.598 (2.575–2.689) | 3378 (3173–3399) | 3536 (3288–3698) |
| B, 100 stored paths | 1 | 3.144 (2.997–3.206) | 2528 (2518–2946) | 2908 (2641–3547) |
| master control | 3 | 6.172 (6.035–6.413) | 1485 (1477–1700) | 1670 (1493–1869) |
| B, 100 stored paths | 3 | 5.087 (4.309–6.306) | 1716 (1671–2515) | 2080 (1894–2554) |

For the valid B sample row, `pg_column_size(result_payload)` was **10,870 B**
and `pg_column_size(simulation_paths.paths)` was **1,352,254 B**. The comparable
Q9a/master recorded values are approximately **11,651 B + 1,460,117 B** for
the split row and **1,471,712 B** for the master payload. This is a measured
storage reduction of about 7.4% for the paths row, not a 10× reduction.

The final three-replica master control did not complete: the warm-up left a row
`PENDING` and the harness exited nonzero. Therefore B-vs-master **WAL per
simulation**, exact status-body bytes from this session, and a clean final
three-replica control are **not investigated**. Q9b already keeps status bodies
near 30.9 KB, so B is not expected to change that response shape. The earlier
diagnostic ceiling of approximately **17 simulations/s** remains context for the
no-payload condition, not a B result.

At three replicas, the only measured API/worker/Kafka/Postgres limitation in the
valid B run is **not investigated**: CPU, Kafka lag, and Postgres counters were
not captured in a clean paired block. The B throughput and latency were within
the observed run spread relative to the master control, so the performance
difference is **not distinguishable**.

## Recommendation

**B is not enough to justify a performance change.** It reduces stored path bytes
by roughly 7.4% in the throwaway row, but the valid three-replica result is not
better than master and WAL was not measured. The evidence supports keeping the
existing Q9a/Q9b design rather than adding a sampled-storage semantic change.

If the owner decides that completion latency matters beyond the current Q9a
improvement, **A is the next experiment**, not an implementation approval:
measure a deferred, idempotent paths writer with explicit `paths_status`,
`202/Retry-After`, and crash/retry tests. C should be considered only for storage
cost, after measuring decompression CPU and WAL.

## Proposed implementation sequence if A is approved

1. Add `paths_status` and an idempotent paths-write command; test state
   transitions and duplicate delivery.
2. Move the paths write behind a durable completion event/outbox; test
   summary completion when the paths writer is delayed or unavailable.
3. Add `/paths` `202`/`Retry-After` behavior and frontend polling/error states.
4. Run the full P0/A protocol at one and three replicas, including WAL, stored
   bytes, status bytes, CPU, Kafka lag, and crash/retry recovery.
5. Roll back by disabling the deferred writer only after deciding how
   `PENDING`/`FAILED` paths are surfaced; do not silently claim paths are ready.

## Owner questions

- Is storing only 100 paths acceptable, knowing existing rows retain 1000 and
  rollback cannot recreate omitted paths?
- What is the maximum `n` the UI will ever request?
- Is a summary `COMPLETED` before paths are `READY` acceptable?
- Should failed path persistence be retryable indefinitely, bounded, or user-visible
  as a permanent partial result?

