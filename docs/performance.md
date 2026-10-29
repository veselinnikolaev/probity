# Performance

Measured, not estimated. Every number on this page was produced by the harness in
`loadtest/` on the machine described below. Provenance: this page condenses the
measurement sessions that were recorded in `docs/PHASE_4_MEASUREMENTS.md` before the
documentation rewrite; the full session logs remain in git history (`git log --
docs/PHASE_4_MEASUREMENTS.md`).

## 1. Method (read this before quoting any number)

- **Harness**: `loadtest/run-baseline.ps1` drives `loadtest/k6/baseline.js` with
  `grafana/k6` **v2.3.0** (pinned image; `docker run --rm grafana/k6 version`).
- **Runs**: one discarded warm-up + **3 measured runs per condition**, reported as
  **median with min–max**. `TRUNCATE TABLE simulations` before the warm-up and before
  every measured run (`-ResetBetweenRuns`, default ON), so no run competes with rows
  written by an earlier run.
- **Load shapes**: Scenario A = 6 VUs × 60 simulations (throughput under load);
  Scenario B = 1 VU × 20 simulations (single-flight). Each simulation:
  `numberOfSimulations=1000`, `timeHorizonDays=90`, `confidenceLevel=0.95`, 2 positions.
- **Units**: throughput in **simulations/second (sims/s)**; every latency in
  **milliseconds at p95/p99**.
- **Latency definitions**:
  - `accept_latency_ms` — the `202` round trip (broker write + `PENDING` row).
  - `e2e_latency_ms` — POST → terminal status. It includes the client poll loop, which
    sleeps **200 ms** between polls, so it carries a poll floor of roughly
    `(N−1) × 200 ms`.
  - `server_latency_ms` — `updatedAt − createdAt` from the `COMPLETED` row. It excludes
    the completion `UPDATE`'s execute+commit by construction, so it is a **lower bound**
    on server time, not the full service time.
- **Environment**: **one dev laptop**, Windows + Docker Desktop, **32 logical cores,
  31.6 GB RAM**, shared by k6, the API JVM, Postgres, Kafka and Redis. Observability
  containers were stopped during load runs. **Every figure is relative** — same machine,
  different build or replica count — **and is not capacity.**

### Caveats that travel with every result

1. The load generator competes with the system under test for the same CPU and disk. The
   numbers are good for regression comparison and order-of-magnitude reasoning, not for
   sizing.
2. Poll-based e2e inflates latency by the 200 ms poll granularity. `server_latency_ms` is
   the metric for comparing the worker pipeline, but it **excludes the completion
   write**, measured at **0.13–3.2 s** on a dirty table. `e2e_latency_ms` is the only
   metric that includes the payload write.
3. The `simulations` table grows **≈ 7.89 MB per completed simulation** (bytes-per-row
   including TOAST and indexes; raw JSON ≈ 4.27 MB). All numbers below are measured on a
   freshly truncated table unless stated otherwise.
4. Only the happy path is load-tested. The park/seek and DLT paths are covered by
   integration tests, not by this load profile.
5. Condition order was not counterbalanced (P0 → P1 → P2, P0 → Q9a), so small gaps share
   any ordering drift; the large gaps are far outside it.

## 2. Payload size (measured)

One `COMPLETED` row at the default shape (1000 paths × 90 days), Postgres 16:

| Figure | Value |
|---|---|
| raw JSON — `octet_length(result_payload::text)` | **4,270,030 B ≈ 4.27 MB** |
| stored jsonb — `pg_column_size(result_payload)` | **1,472,297 B ≈ 1.47 MB** |
| per-row table share — `pg_total_relation_size / count(*)` | **7,893,811 B ≈ 7.89 MB** |
| HTTP status body — `curl -w "%{size_download}"` | **3,902,532 B ≈ 3.90 MB** |

MB = 10⁶ bytes. The label matters: the completion `UPDATE` writes the *stored* payload
(≈ 1.47 MB) while the client downloads the *response* body (≈ 3.90 MB). They are
different sizes, and conflating them produced wrong conclusions in earlier notes.

## 3. Baseline — one process, one consumer

Pre-extraction build (API and async listener in one JVM):

| Metric | Session 1 median (min–max) | Session 2 median (min–max) |
|---|---|---|
| A throughput (sims/s) | **1.562** (1.552–1.569) | **1.606** (1.498–1.613) |
| A e2e p95 (ms) | 5,375.1 (5,043.4–6,761.1) | 5,135.1 (4,762.8–6,076.5) |
| A server p95 (ms) | 3,913.2 (3,697.1–5,449.4) | 4,145.3 (3,433.2–4,972.7) |
| A accept p95 (ms) | 50.3 (27.6–55.2) | 36.4 (24.2–58.3) |
| B throughput (sims/s) | **0.863** (0.839–0.881) | **0.872** (0.864–0.894) |
| B e2e p95 (ms) | 1,356.2 (1,293.6–1,386.3) | 1,324.2 (1,323.1–1,343.2) |
| B server p95 (ms) | 50.1 (43.7–52.8) | 34.1 (32.2–35.2) |

60/60 (A) and 20/20 (B) simulations completed in every run, zero failures.

**Reading:** the single consumer thread is the bottleneck. Six concurrent clients plateau
at ~1.6 completed sims/s, and `server_latency_ms` p95 rises from ~34–50 ms single-flight
to ~3.9–4.1 s at concurrency 6. The accept path stays in the tens of milliseconds and is
not the constraint. Scenario B is completion-write bound, not a poll artifact: the
Monte Carlo itself finishes in ~30–80 ms; the variable term is the completion `UPDATE`
(0.13–3.2 s on a dirty table).

Splitting the worker into a second JVM did not change single-consumer throughput
(1.405/1.527 sims/s at 1 consumer); running **two** consumers (API + worker) raised
Scenario A to 2.125/2.472 sims/s (+36 %/+54 %). That was the shipped behaviour before the
API listener was gated out (ADR 0002).

## 4. Replica scaling is sub-linear

Worker only, containers on Docker host networking, Scenario A:

| Replicas | Partitions/replica | Throughput (sims/s, median [min–max]) | accept p95/p99 (ms) | e2e p95/p99 (ms) | server p95/p99 (ms) |
|---|---|---|---|---|---|
| 1 | 6 | 1.724 [1.653–1.776] | 43.1 / 51.0 | 4,612.6 / 5,506.0 | 3,592.6 / 4,576.3 |
| 3 | 2/2/2 | 3.155 [2.679–3.184] | 47.5 / 892.0 | 4,230.8 / 5,149.8 | 3,254.5 / 4,060.5 |
| 6 | 1 each | 3.770 [3.514–4.340] | 69.4 / 1,084.2 | 2,388.1 / 4,085.1 | 1,570.3 / 1,775.0 |

Scaling: **1 → 3 = 1.83x**, **3 → 6 = 1.19x**, **6 = 2.19x of 1**. The partition ceiling
was never reached (ownership observed per topology: `[0..5]`, `[0,1][2,3][4,5]`, one
each). A higher-concurrency probe (18 VUs × 90 iterations) gave **2.97 sims/s at 6
replicas**, lower than at 6 VUs, so the plateau is server-side — not the load generator
and not the partition count. Whether the shared Postgres (checkpoint/WAL I/O) or the API
accept path binds is **not investigated**.

`accept_latency_ms` p99 degrades monotonically with replicas (51 → 892 → 1,084 ms) while
e2e and server latency improve. Scenario B is flat at **1.079 / 1.049 / 1.036 sims/s**
for 1/3/6 replicas — by construction, one VU runs one simulation to completion.

## 5. Write amplification (the real single-replica constraint)

WAL measured with `pg_current_wal_insert_lsn()` before/after and `pg_wal_lsn_diff()`
(exact, no sampling), cross-checked against `pg_stat_wal` after a ≥ 20 s settle; both
methods agree within 0.7 %.

**One simulation** (1000 paths, 90 days):

| Quantity | Value |
|---|---|
| WAL (LSN diff) | **4,506,280 B** |
| `pg_stat_wal.wal_bytes` (settled) | 4,539,255 B (agree within **0.7 %**) |
| stored payload | 1,471,708 B |
| raw payload | 4,270,549 B |
| **WAL : stored** | **≈ 3.06x** |

**One Scenario A run** (60 completions): WAL total **1,364,749,048 B = 22.75 MB per
simulation**; full-page images account for ≈ **73 %** of it; `checkpoints_req` +2 with
`checkpoint_write_time` ≈ 132,731 ms. Under sustained load the per-simulation WAL
balloons from ≈ 4.5 MB to ≈ 22.7 MB (≈ 15x the stored payload). The cause is the write
pattern plus Postgres defaults (`max_wal_size = 1024 MB`,
`checkpoint_timeout = 300 s`, `full_page_writes = on`, `wal_compression = off`): the run
forces checkpoints, and each dirty page is rewritten whole. This is a Postgres
configuration + write-pattern effect, **not worker CPU**.

**Verdict on CPU-based HPA:** the re-check does not clear the concern. Write
amplification is real at both scales, so CPU utilisation may saturate late or not at all.
The HPA ships as recorded and **no scaling behaviour is claimed**.

## 6. Write cost vs response cost (P0/P1/P2 control)

The payload-write diagnostic removed the write and the response together and measured
≈ 10x. To separate them, a third condition kept the full write and shrank only the
response. Scenario A, 60 simulations:

| Condition | Replicas | Throughput (sims/s) | e2e p95 (ms) | WAL/sim (MB) | `/status` bytes | stored/sim |
|---|---|---|---|---|---|---|
| P0 full write, full response | 1 | **1.892** (1.811–1.928) | 4,376.1 | 23.56 | 3,901,505 | 1.47 MB |
| P1 no payload | 1 | **16.884** (16.373–18.735) | 647.0 | 0.0029 | 630 | 424 B |
| **P2 full write, small response** | 1 | **2.074** (2.064–2.105) | 3,821.1 | 23.84 | 731 | 1.47 MB |
| P0 | 3 | **3.272** (3.130–3.324) | 2,856.3 | 22.11 | 3,901,916 | 1.47 MB |
| P1 | 3 | **17.335** (16.522–17.838) | 663.7 | 0.0029 | 630 | 424 B |
| **P2** | 3 | **3.689** (3.581–3.989) | 3,188.2 | 22.80 | 731 | 1.47 MB |

**P2 ≈ P0** (+10 %/+13 %, non-overlapping ranges) and **P1 is the outlier** (≈ 8.1x P2 at
1 replica). Removing the response alone is worth ≈ 10 %; removing the payload write is
what moves throughput by an order of magnitude. `checkpoints_req` agrees: 7–8 per three
runs for P0/P2, **0** for P1. The P1 plate at 3 replicas was measured: 300/300
iterations, 0 failures, lag 0, only the API near a full core (≈ 1.05 cores), Postgres
0.14–6.3 %, Kafka 1.5–3.7 %, host 17–34 % busy. Whether the API is capped by CPU or a
serialisation point is **not investigated**.

## 7. Q9a — split the write (API contract unchanged)

`allPaths` moved to `simulation_paths`; payload and paths are written in the same
transaction. Scenario A:

| Measure | P0 @ 1 | Q9a @ 1 | P0 @ 3 | Q9a @ 3 |
|---|---|---|---|---|
| Throughput (sims/s) | 1.767 (1.658–1.779) | **2.670 (2.592–2.684)** | 3.161 (3.065–4.418) | **5.377 (4.867–5.594)** |
| e2e p95 / p99 (ms) | 4,599.5 / 4,876.5 | 2,959.9 / 3,319.1 | 3,251.6 / 3,994.2 | 1,581.7 / 2,001.9 |
| server p95 / p99 (ms) | 3,515.7 / 3,887.0 | 2,231.0 / 2,536.8 | 2,350.4 / 3,118.4 | 835.1 / 1,204.8 |
| WAL/sim (MB) | 23.819 | **1.539** | 20.918 | **1.528** |
| api CPU (s/run) | 9.27 | 9.16 | 15.37 | 8.03 |
| worker CPU (s/run) | 7.55 | 18.49 | 11.01 | 18.67 |
| stored payload / paths per sim | 1,471,772 / 0 | **11,646 / 1,460,208** | 1,471,712 / 0 | **11,651 / 1,460,117** |

- Throughput **+51 % at 1 replica**, **+70 % at 3**; ranges do not overlap in either
  block.
- WAL per simulation **−93.5 %** at 1 replica and **−92.7 %** at 3, with requested
  checkpoints nearly disappearing. Why identical logical bytes cost that much less WAL is
  **not investigated**; the number is the measurement, not a mechanism.
- Nothing is lost: the two stored parts total 1,471,854 B/sim (1 replica) and 1,471,768
  B/sim (3 replicas), the same ≈ 1.47 MB as before, now as ≈ 11.6 KB inline + one
  ≈ 1.46 MB paths row.
- The response contract is unchanged (status bodies 3,901,535 / 3,901,573 B vs P0's
  3,901,906 / 3,902,045 B, inside the ± ~140 B random-data spread).
- Worker CPU per 60-simulation run **rose** (7.55 → 18.49 s at 1 replica; 11.01 → 18.67 s
  at 3): the split path does more worker-side work per simulation. Mechanism **not
  investigated**.

## 8. Q9b — stop returning all paths

`allPaths` is out of the status/detail/list bodies; `GET /{id}/paths?n=N` returns an
evenly spaced sample plus the global min and max paths. Scenario A:

| Condition | Throughput (sims/s) | e2e p95 / p99 (ms) | status body (B) | WAL/sim (MB) |
|---|---|---|---|---|
| P0 @ 1 | 2.212 [2.134–2.232] | 3,653.8 / 4,239.7 | 3,901,679 | 1.528 |
| Q9b @ 1 | **3.135** [3.130–3.172] | 2,486.1 / 2,934.3 | **30,948** | 1.551 |
| P0 @ 3 | 4.754 [4.725–4.880] | 1,994.4 / 2,319.6 | 3,901,379 | 1.530 |
| Q9b @ 3 | **6.410** [6.380–6.533] | 1,460.0 / 1,744.2 | **30,978** | 1.559 |

- Throughput **+42 % at 1 replica**, **+35 % at 3**, with non-overlapping ranges. It is
  reported as a measurement, not a promise.
- `GET /{id}/status` is **≈ 126x smaller** (3,901,679 → 30,948 B). The portfolio list
  drops from **234,087,457 B to 1,841,051 B** for the same ≈ 60 rows. The on-demand
  `/paths?n=100` sample is **387,300 B** (1 replica) / **386,919 B** (3 replicas),
  `sampleSize=100` of `totalPaths=1000`.
- WAL is flat (1.528 → 1.551 MB/sim, ranges overlap) — Q9b changes the read/DTO side, not
  the completion write.
- API CPU per run fell (11.14 → 3.19 s at 1; 8.94 → 2.84 s at 3), consistent with
  serialising ~30 KB instead of ~3.9 MB per poll. Why worker CPU also fell is **not
  investigated**.
- Runtime contract check: `n=0`, `n=1001`, `n=2000` → `400`; `n` omitted → `200` with the
  default 100; unknown id → `404`; no auth → `401`; `Cache-Control: no-store, private`,
  no ETag; two identical calls byte-identical; global min/max included in the sample.

## 9. Rolling restart under load

3 replicas, 6 VUs × 200 iterations, one replica `docker stop`ped (SIGTERM) and replaced:

- `docker stop` returned in **0.6 s**, exit **143** (graceful); log order
  `unsubscribed from all topics` → `Consumer stopped` → `Graceful shutdown complete`.
- Replacement owned all 6 partitions **11.1 s** after start (group rebalance).
- After a 25 s settle: **200 rows, all `COMPLETED`**; `simulation-requested.DLT` empty
  (all partitions at offset 0); consumer lag **0**.
- Client-visible cost only: **6 of 200** iterations exceeded the 150-poll (~30–35 s)
  budget; every one reached `COMPLETED` afterwards, slowest **49.1 s**.
- Deterministic in-flight test: SIGTERM issued 0.1 s after `Received simulation request
  event`; the simulation completed **0.53 s later on the same replica**, the offset was
  acked, then the consumer closed. On a graceful SIGTERM an in-flight simulation
  completes and does not need the 12 min claim lease; the park/seek + lease path remains
  the backstop for an ungraceful kill (not exercised here).

Settings: `max.poll.interval.ms = 600000` (10 m), claim lease 12 m,
`ack-mode: MANUAL_IMMEDIATE`, `max.poll.records: 1`, no listener shutdown timeout, and
`k8s/simulation-worker-deployment.yaml` sets `terminationGracePeriodSeconds: 660`. Two
stops in an earlier mid-rebalance state took **21.1 s** (idle) and **24.1 s** (loaded);
why that path is slow is **not investigated**.

## 10. What is not claimed

- No capacity numbers, no production hardware, no SLOs.
- No claim that the sub-linear replica scaling is understood. Candidate causes (shared
  Postgres checkpoint/WAL I/O, API serialisation point) were not isolated.
- No claim that the Q9a WAL drop is explained by any mechanism; only the measurement is
  recorded.
- No claim about the worker-side CPU increase in Q9a, or the worker CPU decrease in Q9b.
- No frontend performance measurements are recorded here.
