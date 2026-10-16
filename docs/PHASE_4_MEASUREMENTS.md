# Phase 4 — PR-D0 worker prerequisites: 1-replica baseline

Measurements recorded for PR-D0 (`pr-d0-worker-prerequisites`, Phase 4 §2.9).
PR-D0 ships the worker **prerequisites only** — topic partitions, delivery-timeout
invariants, the unclaimable park/seek path, and a poison-payload DLT route. It does
**not** change the worker's actual concurrency, so this baseline is the "before"
number that PR-D1+ (separate worker image, HPA) is meant to move.

The numbers are produced by `loadtest/run-baseline.ps1`, which is deliberately
**not** a single run: a single k6 run on a shared laptop is far too noisy to compare
against. The script does one discarded warm-up run, then **3 runs each** of Scenario
A and Scenario B, and reports the **median and the min–max** of throughput, accept,
e2e and server latency at both **p95 and p99**. The `simulations` table is
`TRUNCATE`d before the warm-up **and before every measured run** (`-ResetBetweenRuns`,
default ON), so each run starts from a ~empty table instead of competing with rows
written by earlier runs. This document records **two independent sessions** to show
the session-to-session spread as well as the within-session spread.

> **Post-PR-D2 note (2026-10-13).** Every number below was recorded on the
> **pre-extraction single JVM** — the PR-D0 build, one process running both the API
> and the async listener. PR-D2 has since split the worker into its own process
> (`probity-worker`), and these figures have **not** been re-measured against the
> split. Do **not** compare numbers across the split without re-running
> `loadtest/run-baseline.ps1` against the new topology. No recorded number has been
> changed; they still describe the PR-D0 1-replica baseline.
>
> **Re-measured against the split (2026-10-13).** The figures above remain the
> pre-extraction reference and are unchanged; the split re-measurement is recorded in
> *Split baseline* below.

## Topology under test

| Item | Value |
| --- | --- |
| App replicas | **1** (single JVM host process, `java -jar probity-api/target/probity-api-0.0.1-SNAPSHOT.jar`) |
| Consumer threads | **1** (`spring.kafka.listener.concurrency` unset → default) |
| `simulation-requested` partitions | **6** (`probity.kafka.simulation-topic-partitions: 6`) |
| `max.poll.interval.ms` | `600000` (10m) |
| `probity.simulation.claim-lease` | `12m` (LEASE > poll-interval invariant) |
| `probity.simulation.unclaimable-backoff` | `1s` |
| Consumer value deserializer | `ErrorHandlingDeserializer` → `JsonDeserializer` delegate |
| Broker | `apache/kafka:3.7.0` (KRaft), single node, `localhost:9092` |
| Store / cache | `postgres:16` and `redis:7.2.4` (compose), localhost |
| Host | Windows + Docker Desktop, dev laptop — **not** a production-hardware figure |
| k6 load generator | `grafana/k6` **v2.3.0** (recorded `docker run --rm grafana/k6 version`; the driver's `--summary-export` is deprecated in newer releases but still works with this image — keep it pinned, not `:latest`) |

Load shape per simulation: `numberOfSimulations=1000`, `timeHorizonDays=90`,
`confidenceLevel=0.95`, against a seeded portfolio with 2 positions
(`AAPL ×10`, `GOOGL ×5`).

The Grafana/Prometheus/Loki containers were **stopped** for these sessions; only
`kafka`, `postgres` and `redis` ran alongside the app and k6. Loki in particular was
in a restart loop and added avoidable CPU/I-O noise.

### What each latency metric measures

`accept_latency_ms` is the 202 round trip (broker write + `PENDING` row).
`e2e_latency_ms` is POST → terminal status and therefore carries a **status-poll
floor** (the k6 loop sleeps 200 ms between polls; a simulation needing N polls
carries roughly `(N−1) × 200 ms`).

`server_latency_ms` is `updatedAt − createdAt` read straight from the `COMPLETED`
status payload. `createdAt` is the `PENDING` insert; `updatedAt` is the timestamp the
completion write **stamps**, captured just before that `UPDATE` runs
(`SimulationRepository.complete()` → `now()`). So it excludes the client poll floor
*and* the completion `UPDATE`'s own execute+commit time — treat it as a **lower bound**
on the true server-side time, not the full service time. That exclusion is material:
Postgres logged the completion `UPDATE`'s execute at **0.13 s ↔ 3.2 s** on this host
while `updatedAt − createdAt` stayed ~30–80 ms in the same runs (see the Scenario B
trace below). The lower bound is still the right tool for comparing the worker pipeline
across builds — it just does not capture the payload write.

### What this baseline represents

The numbers below are the **best case**: `-ResetBetweenRuns` starts every run on a
freshly `TRUNCATE`d `simulations` table, so no run competes with rows written by an
earlier one. They are **not** steady-state behaviour on a grown table — when the
table was left bloated (≈ 6 GB after ~1 000 sims), the completion `UPDATE` stalled
for **2.6–3.2 s** and throughput fell from ~1.14 to ~0.66 sims/s within a session
(the *degradation case*, kept below under *Payload write cost*). **PR-D1 must beat
the single-consumer ceiling of ~1.5–1.6 sims/s** (service time ≈ 0.64 s per
simulation), not the degraded figures.

## Scenario A — concurrency 6 (throughput under load)

60 simulations across 6 VUs, per run. Two sessions × 3 runs.

| Metric | Session 1 — median (min–max) | Session 2 — median (min–max) |
| --- | --- | --- |
| throughput (sims/s) | **1.562** (1.552–1.569) | **1.606** (1.498–1.613) |
| `accept_latency_ms` p95 | 50.3 (27.6–55.2) | 36.4 (24.2–58.3) |
| `accept_latency_ms` p99 | 59.6 (48.7–63.5) | 95.2 (38.7–102.3) |
| `e2e_latency_ms` p95 | 5375.1 (5043.4–6761.1) | 5135.1 (4762.8–6076.5) |
| `e2e_latency_ms` p99 | 5664.7 (5526.8–7299.0) | 5424.3 (5249.2–6641.2) |
| `server_latency_ms` p95 | 3913.2 (3697.1–5449.4) | 4145.3 (3433.2–4972.7) |
| `server_latency_ms` p99 | 4619.7 (4338.5–6213.6) | 4220.9 (4092.0–5453.0) |

- `simulations_completed`: **60/60 in every run**; `simulations_failed`: **0**;
  `http_req_failed`: **0.00 %**.
- Per-run throughput is **flat within a session** with `-ResetBetweenRuns`: session 1
  ran `1.569 / 1.552 / 1.562`, session 2 `1.606 / 1.498 / 1.613` sims/s. The old
  40 %+ within-session collapse (recorded *without* per-run resets, `1.14 → 0.80 →
  0.66`) is gone; what remains is ±5 % host noise, not table growth (see *Payload write
  cost*).

## Scenario B — concurrency 1 (single-flight)

20 simulations on 1 VU, per run. Two sessions × 3 runs.

| Metric | Session 1 — median (min–max) | Session 2 — median (min–max) |
| --- | --- | --- |
| throughput (sims/s) | **0.863** (0.839–0.881) | **0.872** (0.864–0.894) |
| `accept_latency_ms` p95 | 35.8 (28.0–54.7) | 37.7 (37.1–54.5) |
| `accept_latency_ms` p99 | 62.4 (42.4–65.3) | 53.8 (48.3–61.3) |
| `e2e_latency_ms` p95 | 1356.2 (1293.6–1386.3) | 1324.2 (1323.1–1343.2) |
| `e2e_latency_ms` p99 | 1373.6 (1317.1–1390.1) | 1327.2 (1324.6–1421.4) |
| `server_latency_ms` p95 | 50.1 (43.7–52.8) | 34.1 (32.2–35.2) |
| `server_latency_ms` p99 | 68.7 (66.8–79.4) | 35.6 (34.4–37.4) |

- `simulations_completed`: **20/20 in every run**; `simulations_failed`: **0**;
  `http_req_failed`: **0.00 %**.
- **Scenario B is completion-write bound, not a capacity or poll artifact.** The
  worker's Monte Carlo itself is fast (`server_latency_ms` p95 ≈ 34–50 ms), but that
  metric stamps `updatedAt` *before* the completion `UPDATE` runs and so excludes its
  execute+commit, which Postgres logged at **0.13 s ↔ 3.2 s** on this host when the
  table was dirty. In those slow iterations the row genuinely sat in `PROCESSING`
  while the `UPDATE` was executing — the number of PROCESSING polls the client saw
  correlated exactly with the logged `UPDATE` duration (3 polls at ~130–150 ms, 15–17
  polls at ~2.6–3.2 s). With `-ResetBetweenRuns` each run starts on a clean table,
  the slow completions do not occur, and B runs at ~0.86 sims/s with `e2e_latency_ms`
  p95 ≈ 1.35 s. Do not read B as "one simulation takes 2.2 s of server work".

### Where the e2e gap actually goes (Scenario B trace)

The ~3.5 s gap between `server_latency_ms` p95 (~70 ms) and `e2e_latency_ms` p95
(~3.6 s) was decomposed by tracing a real single-flight run (k6 client, per-poll
status times, `serverLatencyMs`, plus the Postgres slow-query log and a k6
payload-GET timer):

| component | measured cost |
| --- | --- |
| accept (POST → 202) | ~20–64 ms |
| status poll floor | 2 × 200 ms sleeps ≈ 400 ms (fast path); `(N−1) × 200 ms` ≈ up to 3.2 s (slow path) |
| completion `UPDATE` execute+commit (persists the stored jsonb, ≈ 1.47 MB/row) | **0.13 s ↔ 3.2 s** — the dominant, variable term; `server_latency_ms` excludes it |
| final COMPLETED poll (downloads the ≈ 3.90 MB status JSON body) | ~0.45–0.55 s via k6 (host.docker.internal); ~0.3 s via localhost `curl` |

Fast iterations (UPDATE ≈ 130–150 ms) therefore end-to-end at ~0.9–1.1 s; slow
iterations (UPDATE ≈ 2.6–3.2 s) at ~2.8–4.0 s. The old wording — "B is poll-bound, the
gap is the client poll loop and the multi-MB status response" — was **incorrect**: the
poll floor and payload transfer are a fixed ~0.6–0.9 s; the variable ~2.5–3 s is the
completion write's commit, which **looks bound by Postgres WAL/disk I/O** (no lock
waits were logged; autovacuum and checkpoint contention were not ruled out), and is
invisible to `server_latency_ms` by construction.

The re-recorded sessions below confirm the attribution: with `-ResetBetweenRuns` every
run starts on a clean table, the slow (2.6–3.2 s) completion writes did **not** occur,
and B's `e2e_latency_ms` p95 collapsed from ~3.6 s to **~1.35 s** at ~0.86 sims/s — the
residual e2e is accept + ~0.4 s poll floor + ~0.5 s final payload download.

## How to read this

1. **The single consumer thread is the bottleneck.** Six concurrent clients plateau
   at ~1.6 completed sims/s while each waits behind the one consumer, which is why
   `server_latency_ms` p95 balloons from ~34–50 ms (single-flight) to ~3.9–4.1 s
   (6-way) and `e2e_latency_ms` p95 from ~1.35 s to ~5.1–5.4 s. The 6 partitions are
   provisioned by PR-D0 but only one is consumed at a time until a later PR raises
   `concurrency` or scales replicas.
2. **`accept_latency_ms` stays low** (p95/p99 tens of ms, occasionally ~100 ms under
   contention) — the request side (broker write + `PENDING` row) is not the constraint.
3. **`server_latency_ms` is a poll-floor-free lower bound.** For single-flight it is
   ~34–50 ms, but it excludes the completion `UPDATE`'s execute+commit — measured at
   0.13–3.2 s here on a dirty table — so it understates end-to-end server time by
   exactly the payload write (see *Payload write cost*). Use it to compare the worker
   pipeline, not to bound `e2e_latency_ms`.
4. **No failures, no stuck rows in the recorded runs.** All simulations ended
   `COMPLETED`; no unclaimable-park activity occurred, so this run exercises the happy
   path only. The park/seek path and the poison→DLT path are covered by the new tests,
   not by this load profile.

### Payload size (measured)

Measured on one `COMPLETED` row at the default shape (1000 paths × 90 d), on
Postgres 16 with the PR-D0 build running (2026-10-11):

| figure | value |
| --- | --- |
| raw JSON text — `octet_length(result_payload::text)` | **4,270,030 B ≈ 4.27 MB** |
| stored in Postgres — `pg_column_size(result_payload)` (compressed jsonb, TOASTed) | **1,472,297 B ≈ 1.47 MB** |
| per-row table share — `pg_total_relation_size('simulations') / count(*)` (includes TOAST + indexes + heap slack; measured on a fresh 20-row table) | **7,893,811 B ≈ 7.89 MB** |
| HTTP status response body — `curl -w "%{size_download}"` on `GET /api/v1/simulations/{id}/status` | **3,902,532 B ≈ 3.90 MB** |

MB = 10⁶ bytes, the unit curl/k6 report. The older unlabelled figures in earlier
notes are not reproducible as single numbers: "~7.3 MB/row" came from the bytes-per-row
method (`pg_total_relation_size / count`) on a dirtier, partly-bloated table, and
"~3.9 MB" was the HTTP response body, **not** the stored jsonb (≈ 1.47 MB). The label
matters because the completion `UPDATE` writes the **stored** payload while the client
downloads the **response** body — they are different sizes.

### Payload write cost (why the session used to degrade)

Every `COMPLETED` simulation persists the full Monte Carlo result: raw JSON text ≈
**4.27 MB**, stored in Postgres as compressed jsonb ≈ **1.47 MB** per row, ≈ **7.89 MB**
bytes-per-row including TOAST + indexes, and ≈ **3.90 MB** as the serialised JSON
status response body (see *Payload size (measured)* above). The completion
`UPDATE` that writes it is the dominant variable cost in this baseline (0.13 s ↔ 3.2 s
execute on this host — PG slow-query log; see the Scenario B trace). Consequences:

- The completion write **looks bound by Postgres WAL/disk I/O, not worker CPU** (no
  lock waits were logged; autovacuum and checkpoint contention were not ruled out):
  the Monte Carlo itself finished in ~30–80 ms in the same runs.
- Cheapest next check for PR-D1: capture `pg_stat_wal` and checkpoint counters
  (`pg_stat_bgwriter`, `pg_stat_checkpointer`) around a run, or repeat Scenario B with
  the payload column excluded from the write, before concluding the write is
  WAL/disk-bound.
- **Degradation case** — runs recorded *without* per-run resets drifted within a
  session — later runs slower (`1.14 → 0.80 → 0.66` sims/s in Scenario A). The
  re-recorded numbers below use `-ResetBetweenRuns` and report whatever trend
  *remains*.
- **Degradation case** — a **bloated** table is actively dangerous for the load test:
  after ~1 000 sims the table was 6.3 GB and the worker's completion `UPDATE` stalled
  in Postgres on `wait_event = WALInitSync` (disk/WAL I-O) with autovacuum contending
  for the same page. The simulation sat in `PROCESSING`, missed k6's 30 s poll
  ceiling, and tripped `simulations_failed` → `abortOnFail`.
- Therefore `run-baseline.ps1` **truncates by default**: `-ResetBetweenRuns` (ON by
  default) `TRUNCATE`s `simulations` before the warm-up **and before every measured
  run**, so each run starts from a ~empty table and runs no longer compete with rows
  written by earlier runs. Old `-ResetSimulations` spellings still work (alias). Pass
  `-ResetBetweenRuns:$false` only when the growth itself is what you are measuring.

### Session-to-session spread

The two recorded sessions agree closely (A throughput median 1.562 vs 1.606; B 0.863
vs 0.872), so the *method* is reproducible. Absolute values are still host-load
dependent: an earlier session on the same laptop measured Scenario A around **2 sims/s**
with a long-running JVM, and the sessions recorded *without* per-run resets were ~2×
slower (A 0.79–0.80, B 0.44–0.45 sims/s) because payload writes then dominated
(degradation case — same root cause as the 2.6–3.2 s completion `UPDATE`s above). Treat
the medians here as the contemporaneous baseline and always compare future runs on the
same host, shape and reset setting.

## Split baseline (PR-D2 build, API + worker; 2026-10-13)

Re-measured after the PR-D2 split with the **same method** as the pre-extraction
baseline above: `loadtest/run-baseline.ps1`, two independent sessions, one discarded
warm-up + 3 runs each of Scenario A (6 VUs × 60) and Scenario B (1 VU × 20),
`-ResetBetweenRuns` ON, Grafana/Prometheus/Loki containers stopped, k6 `grafana/k6`
**v2.3.0** (`docker run --rm grafana/k6 version`). Both processes are `java -jar` on
the default JVM with **no extra flags** (Java 21.0.9):
`probity-api/target/probity-api-0.0.1-SNAPSHOT.jar` (:8080) and
`probity-worker/target/probity-worker-0.0.1-SNAPSHOT-exec.jar` (:8081). Units are
unchanged — throughput in simulations/second (sims/s), every latency in milliseconds
at p95/p99 (`accept`/`e2e`/`server` as defined in *What each latency metric measures*).

The PR-D2 build runs the `@KafkaListener` in **two** processes unless the API's
listener is disabled, so two topologies were measured (root cause in the technical
report's PR-D2 *Finding*):

- **(i) One consumer — intended concurrency 1.** API started with the config-only flag
  `SPRING_KAFKA_LISTENER_AUTO_STARTUP=false`, worker unchanged. Runtime verification
  (`kafka-consumer-groups --describe --group probity-simulation-worker --members`):
  **1 member, 6 partitions**, lag 0.
- **(ii) Two consumers — what PR-D2 actually shipped.** API and worker both start the
  listener; the same command showed **2 members, 3 partitions each**.

### (i) One consumer (API listener disabled)

Scenario A — 6 VUs × 60 sims, 3 runs per session:

| Metric | Session 1 — median (min–max) | Session 2 — median (min–max) |
| --- | --- | --- |
| throughput (sims/s) | **1.405** (1.385–1.420) | **1.527** (1.383–1.768) |
| `accept_latency_ms` p95 | 57.5 (35.3–58.0) | 45.1 (38.1–53.2) |
| `accept_latency_ms` p99 | 66.1 (62.4–169.2) | 95.4 (46.8–1948.6) |
| `e2e_latency_ms` p95 | 6062.2 (5759.5–6224.8) | 5486.3 (4837.0–5766.7) |
| `e2e_latency_ms` p99 | 6731.5 (6693.0–6827.6) | 6861.3 (5794.8–7365.0) |
| `server_latency_ms` p95 | 4710.0 (4531.0–5243.4) | 4121.0 (3948.9–4518.7) |
| `server_latency_ms` p99 | 5545.2 (5378.9–5915.5) | 5431.8 (4873.8–6241.5) |

Scenario B — 1 VU × 20 sims, 3 runs per session:

| Metric | Session 1 — median (min–max) | Session 2 — median (min–max) |
| --- | --- | --- |
| throughput (sims/s) | **0.885** (0.846–0.886) | **0.888** (0.879–0.907) |
| `accept_latency_ms` p95 | 46.6 (32.7–67.3) | 29.1 (25.5–169.9) |
| `accept_latency_ms` p99 | 54.9 (42.5–86.3) | 47.5 (45.8–411.6) |
| `e2e_latency_ms` p95 | 1368.6 (1295.1–1503.0) | 1330.5 (1275.3–1360.9) |
| `e2e_latency_ms` p99 | 1408.1 (1402.2–1517.4) | 1383.7 (1279.9–1693.0) |
| `server_latency_ms` p95 | 91.3 (66.8–101.3) | 58.8 (48.0–92.4) |
| `server_latency_ms` p99 | 121.1 (78.2–126.3) | 78.4 (70.2–524.1) |

- `simulations_completed`: 60/60 (A) and 20/20 (B) in every run; `simulations_failed`:
  **0**; `http_req_failed`: **0.00 %**. Consumer lag after the last run: **0** on all 6
  partitions.
- Process CPU during Scenario A (method: `(Get-Process …).CPU` seconds delta over a
  15.1 s window; unit = cores): **API 0.60**, **worker 0.28** (≈ 0.88 cores total).

### (ii) Two consumers (API + worker both consuming) — what PR-D2 shipped

Recorded before the API listener was disabled. Throughput only; the run-level failure
counters matched (i): `simulations_failed` 0, `http_req_failed` 0.00 %, end lag 0.

| Scenario | Session 1 — throughput median (min–max) | Session 2 — throughput median (min–max) |
| --- | --- | --- |
| A (6 VUs × 60) | **2.125** (2.075–2.497) | **2.472** (2.376–2.600) |
| B (1 VU × 20) | **0.960** (0.943–0.970) | **0.980** (0.941–1.009) |

- Process CPU during Scenario A (same method): **API 0.98**, **worker 0.23**
  (≈ 1.21 cores total).

### Comparison against the pre-extraction single JVM

Throughput (sims/s) — Session 1 / Session 2 medians:

| Topology | Scenario A | Scenario B | Δ vs pre-extraction |
| --- | --- | --- | --- |
| Pre-extraction single JVM, 1 consumer | 1.562 / 1.606 | 0.863 / 0.872 | — (reference) |
| Split (i): API listener off, 1 consumer | 1.405 / 1.527 | 0.885 / 0.888 | A −10 %/−5 %, B +3 %/+2 % |
| Split (ii): API + worker, 2 consumers | 2.125 / 2.472 | 0.960 / 0.980 | A +36 %/+54 %, B +11 %/+12 % |

- **(i) one consumer — not distinguishable from the pre-extraction single JVM.**
  Every (i) median is inside the run-to-run spread seen on this host: the
  pre-extraction A session-2 range was 1.498–1.613 and the (i) A session-2 range was
  1.383–1.768, which brackets it; the B medians differ by less than 3 %. Moving the
  listener into a second `java -jar` did **not** measurably change throughput or
  latency at concurrency 1.
- **(ii) two consumers — distinguishable; throughput is higher.** A ≈ +36–54 %, B
  ≈ +11–12 %, and Scenario A `server_latency_ms` p95/p99 is lower (≈ 2.4–4.1 s vs the
  pre-extraction ≈ 3.9–4.1 s). The group then consumes from two processes, so two
  partitions' work is served concurrently — but whether that is the *cause* of the
  difference (as opposed to host variance) is **not investigated**. Treat (ii) as the
  PR-D2 build's actual behaviour and (i) as the intended single-consumer topology.
- Per-process CPU during A is higher for the API in (ii) (0.98 vs 0.60 cores) because
  the API is then also consuming; worker CPU is similar in both (0.28 vs 0.23). No
  other cause was measured.

## N-replica scaling, rolling restart, and the §2.8.1 WAL re-check (PR-D5 build, 2026-10-15)

Everything above is the PR-D0 1-replica baseline and the PR-D2 split baseline; everything
below is new in PR-D5 and answers §2.8 of `docs/plans/PHASE_4_WORKER_EXTRACTION.md`.
The build is `probity-worker:prd5`, built in this session from `f9e3f33`; there is **no
`src`/`pom`/migration/compose change** since the PR-D4 image, so this is a re-measure of
the same code as the `vesko49/probity-worker:latest` image PR-D4 recorded.

### Method - replicas as containers on host networking (recorded deviation)

The PR-D0 method ran the app as host JVMs. For N > 1 this section ran the replicas as
**containers on Docker host networking** (`--network host`), for two reasons, both
probed:

- a container on the **compose bridge network cannot consume from this Kafka**: the
  broker advertises `PLAINTEXT://localhost:9092`, so a bridge container resolves the
  advertised address to its own loopback and never joins the group (observed: it stayed
  out of `probity-simulation-worker`). This is a pre-existing dev-stack limitation,
  recorded as a follow-up in the PR description - PR-D5 does not change Kafka config.
- `--network host` makes `localhost:9092` the real broker and `localhost:5432` the
  published Postgres, and makes `docker stop` deliver a **real SIGTERM to PID 1** - the
  signal the k8s `terminationGracePeriodSeconds` path produces, which the rolling-restart
  check needs.

Otherwise the recorded method is unchanged: same compose Postgres/Kafka/Redis, same
`loadtest/seed.sql` data, same `loadtest/run-baseline.ps1` driver and `baseline.js`, same
protocol (1 discarded warm-up + 3 measured runs, `-ResetBetweenRuns` ON, `TRUNCATE`
before each). Per replica:

```powershell
docker run -d --name prd5-w1 --network host `
  --health-cmd "wget -qO- http://localhost:8081/actuator/health || exit 1" `
  --health-interval=10s --health-timeout=5s --health-retries=5 --health-start-period=20s `
  -e DB_HOST=localhost -e DB_PORT=5432 -e DB_NAME=probity `
  -e DB_USERNAME=probity_worker -e DB_PASSWORD=<worker role password> `
  -e KAFKA_BOOTSTRAP_SERVERS=localhost:9092 `
  -e SIMULATION_CONSUMER_GROUP=probity-simulation-worker -e SIMULATION_CLAIM_LEASE=12m `
  -e SPRING_DOCKER_COMPOSE_ENABLED=false -e WORKER_PORT=8081 `
  probity-worker:prd5
# replica 2 -> WORKER_PORT=8082, replica 3 -> 8083, ... on the shared host namespace.
```

The `WORKER_PORT` override is required (each replica needs its own management port on the
one host namespace), and the healthcheck override is required because the image hard-codes
8081.

**Test machine:** one dev laptop, **32 logical cores, 31.6 GB RAM**, shared by k6, the API
JVM, Postgres, Kafka and Redis. Every figure below is **relative** (same machine, different
replica count) and is **not capacity**.

### Scenario A - concurrency 6, 60 simulations (throughput under load)

| replicas | partitions / replica | throughput (sims/s, median [min-max]) | accept p95 / p99 (ms) | e2e p95 / p99 (ms) | server p95 / p99 (ms) |
|---|---|---|---|---|---|
| 1 | 6 | 1.724 [1.653-1.776] | 43.1 / 51.0 | 4612.6 / 5506.0 | 3592.6 / 4576.3 |
| 3 | 2 / 2 / 2 | 3.155 [2.679-3.184] | 47.5 / 892.0 | 4230.8 / 5149.8 | 3254.5 / 4060.5 |
| 6 | 1 each | 3.770 [3.514-4.340] | 69.4 / 1084.2 | 2388.1 / 4085.1 | 1570.3 / 1775.0 |

Where the accept/e2e/server metrics are defined is unchanged from the PR-D0 section:
`e2e_latency_ms` is the only metric that includes the completion `UPDATE`'s execute+commit
and the payload write; `server_latency_ms` (`updatedAt - createdAt`) structurally excludes
it and is a lower bound.

Scaling is **sub-linear**: 1 -> 3 replicas is **1.83x**, 3 -> 6 replicas only **1.19x**
(6 replicas = 2.19x of 1). §2.8 expected "near-linear scaling until the partition count is
the ceiling". The partition ceiling was **never reached** - ownership was observed per
topology (`--describe --members`: `[0,1,2,3,4,5]` for 1 replica, `[0,1] [2,3] [4,5]` for
3, one partition each for 6) - so at 6 replicas each worker already owns exactly one
partition, and throughput still barely moved. The ceiling measured is therefore a
**server-side resource shared by all replicas** (Postgres on the same laptop), not the
partition count.

`accept_latency_ms` **degrades monotonically** as replicas grow (p99 51 -> 892 -> 1084 ms),
while e2e and server latency **improve** (the queue drains faster, so a completed
simulation is picked up sooner). The p99 accept blow-up is the one clear regression.

**The plateau is not a k6 client-VU ceiling.** A higher-concurrency probe (18 VUs x 90
iterations, two runs each) gave **2.97 sims/s at 6 replicas** - *lower* than the 3.77
sims/s measured at 6 VUs - and 1.63 sims/s at 1 replica. Doubling client concurrency did
not raise throughput, so the limit is on the server side. Whether the shared Postgres
(checkpoint/WAL I/O) or the API accept path is the binding resource is **not investigated**.

### Scenario B - concurrency 1, 20 simulations (single-flight)

| replicas | throughput (sims/s) | accept p95 / p99 (ms) | e2e p95 / p99 (ms) | server p95 / p99 (ms) |
|---|---|---|---|---|
| 1 | 1.079 [1.064-1.099] | 15.1 / 15.8 | 1115.4 / 1122.8 | 16.1 / 16.8 |
| 3 | 1.049 [1.017-1.100] | 16.0 / 16.0 | 1110.2 / 1112.4 | 18.0 / 18.0 |
| 6 | 1.036 [1.036-1.064] | 14.1 / 14.8 | 1128.1 / 1140.8 | 19.0 / 19.0 |

Scenario B is **flat** at ~1.0-1.08 sims/s regardless of replica count, and that is by
construction: one VU runs one simulation to a terminal state before starting the next, so
the rate is set by the single-flight e2e (dominated by the 200 ms status-poll granularity
plus the run itself), not by how many replicas can consume. Nothing here contradicts the
PR-D0 reading; it does mean Scenario B cannot show replica scaling.

Consumer-group lag was **0** for all 6 partitions at the settled end of every run.

### §2.8.1 re-check - WAL written per simulation vs the stored payload

Method: `pg_current_wal_insert_lsn()` before/after the operation and
`pg_wal_lsn_diff()` (exact, no sampling), **cross-checked** against `pg_stat_wal`, read
only after a **>=20 s settle** - `pg_stat_wal` does not flush with the write, and a read
taken immediately after the write returned 0 (method note, recorded). Unit: bytes.
`stored` = `pg_column_size(result_payload)`; `raw` = `octet_length(result_payload)`.

**One simulation** (default shape: 1000 paths, 90 days, confidence 0.95):

| quantity | value |
|---|---|
| WAL, LSN-diff method | **4,506,280 B** |
| `pg_stat_wal.wal_bytes` (settled) | 4,539,255 B (two methods agree within **0.7 %**) |
| `pg_stat_wal.wal_records` / `wal_full_page_imgs` | 2,136 / 5 |
| stored payload (`pg_column_size`) | 1,471,708 B (≈ 1.47 MB) |
| raw payload (`octet_length`) | 4,270,549 B (≈ 4.27 MB) |
| **WAL : stored** | **≈ 3.06x** |
| WAL : raw | ≈ 1.06x |

So one completion writes about **3x the stored payload** in WAL. This answers §2.8.1's
question directly: **WAL written per simulation *is* several times the stored payload, so
the cost is *how the row is written* (a full rewrite of the TOASTed compressed jsonb into
a new row version), not the hardware.** Note `pg_stat_wal` runs an order of magnitude
fewer WAL records than the LSN method implies bytes (2,136 records for 4.5 MB), i.e. a few
large records (TOAST chunks), which is consistent with that write pattern.

**One Scenario A run** (6 VUs x 60 = 60 completions, 1 replica):

| quantity | value |
|---|---|
| WAL total, LSN-diff method | **1,364,749,048 B** = **22.75 MB / simulation** |
| `pg_stat_wal.wal_bytes` (settled) | 1,343,053,514 B = 22.38 MB / sim (agree within **1.6 %**) |
| `wal_records` / `wal_full_page_imgs` | 5,630,187 / **122,064** |
| WAL attributable to full-page images | ≈ 1.00 GB ≈ **73 %** of the WAL |
| `checkpoints_req` during the run | +2 (`checkpoint_write_time` ≈ 132,731 ms) |

Under sustained load the per-simulation WAL **balloons from ≈ 4.5 MB to ≈ 22.7 MB** (≈ 15x
the stored payload). The extra is checkpoint-driven: `checkpoint_timeout = 300 s` and
`max_wal_size = 1024 MB` mean this 1.36 GB run forces checkpoints, and with
`full_page_writes = on` and `wal_compression = off` each dirty page is then written whole
as a full-page image (~1 GB). This is a **Postgres configuration + write-pattern effect,
not worker CPU**.

**Verdict on Q4 (CPU-based HPA):** the re-check **does not clear** the concern PR-D4
recorded. Write amplification is real at both scales, so CPU utilisation may saturate late
or not at all; the HPA ships **as recorded** and **no scaling behaviour is claimed**.
Candidate follow-ups (none implemented in PR-D5): write the payload once / in chunks,
`wal_compression = on`, tune `checkpoint_timeout`/`max_wal_size`, or act on **Q9** (store a
summary and percentiles instead of all paths). The root cause of the sub-linear replica
scaling (WAL I/O vs. something else) is **not investigated**.

### Rolling restart under load (§2.8)

Two experiments.

**(1) Restart one of 3 replicas mid-load.** 6 VUs x 200 iterations ran against 3 replicas
while one replica was `docker stop`ped (SIGTERM) and then replaced. k6 ran with
`--no-thresholds`: the recorded harness aborts the whole run when `simulations_failed > 0`
(`abortOnFail`), which would truncate exactly the load the restart is supposed to disturb
(a deliberate, recorded deviation).

- stopped replica: `docker stop` returned in **0.6 s**, exit code **143** (graceful);
  log order `unsubscribed from all topics` -> `Consumer stopped` -> `Graceful shutdown
  complete`.
- replacement replica: `/actuator/health` UP, and all 6 partitions owned again
  **11.1 s** after start (the group-rebalance window).
- **DB verdict after a 25 s settle: 200 rows, all `COMPLETED`.** No row left `PENDING`
  with no claim. The `simulation-requested.DLT` topic exists but is **empty** (all 6
  partitions at offset 0) - **no DLT growth**. Consumer lag 0.
- **Zero** `parking partition` / `reclaimed by a peer` lines in any surviving replica: the
  park/seek recovery path was **not** needed in this run.
- Client-visible effect only: **6 of 200** iterations exceeded the harness's 150-poll
  budget (≈ 30-35 s) and were reported by k6 as
  `simulations_failed ... status=PENDING polls=150`; every one of them reached
  `COMPLETED` in the database afterwards (slowest completion 49.1 s from accept). The
  restart **delayed** those responses past the client's poll budget but **lost nothing**.

**(2) Deterministic in-flight SIGTERM.** "Did an in-flight simulation finish?" needs a
simulation provably in flight at SIGTERM, not a lucky window, so a single replica was given
all 6 partitions, load started, and `docker stop` was issued **the moment the replica's log
showed a `Received simulation request event` with no matching `Simulation completed
successfully`** (detected in 0.1 s, so the record was still running):

```
02:20:35.125Z  INFO  [requested-0-C-1] SimulationRequestedEventConsumer : Received simulation request event: simulationId=03627d6a-...
02:20:35.658Z  INFO  [requested-0-C-1] SimulationRequestedEventConsumer : Simulation completed successfully: simulationId=03627d6a-...
02:20:35.668Z  INFO  [requested-0-C-1] KafkaMessageListenerContainer    : probity-simulation-worker: Consumer stopped
02:20:35.670Z  INFO  [tomcat-shutdown] GracefulShutdown            : Graceful shutdown complete
```

The in-flight simulation **completed on the same replica, and its offset was acked, before
the consumer closed** (`docker stop` returned in 0.6 s, exit 143). After the replacement
replica joined, the database held **200/200 `COMPLETED`**, the DLT topic was empty, and
`03627d6a` was `COMPLETED` with a 0.24 s wait. So on a graceful `SIGTERM` an in-flight
simulation **completes** - it does not need the 12 m claim lease. The park/seek +
claim-lease path stays the backstop for an **ungraceful** kill (the lease is the recovery
bound, Q6); that path was not exercised here.

### Graceful-shutdown timing (PR-D4 follow-up: "not investigated")

Recorded worker settings: `max.poll.interval.ms: 600000` (10 m),
`probity.simulation.claim-lease: 12m`, `probity.simulation.unclaimable-backoff: 1s`,
`ack-mode: MANUAL_IMMEDIATE`, `max.poll.records: 1`. The worker configures **no**
`spring.kafka.listener.shutdown-timeout` and **no** Boot graceful-shutdown phase timeout
(Spring defaults apply), while `k8s/simulation-worker-deployment.yaml` sets
`terminationGracePeriodSeconds: 660`.

Measured `docker stop -t 60` (SIGTERM -> exit 143), several occasions:

| case | observed |
|---|---|
| settled group, in-flight record (experiment 2) | in-flight record finished 0.53 s later; `Consumer stopped` 0.01 s after that; `docker stop` returned in 0.6 s |
| settled group (no in-flight record) | `docker stop` returned in 0.6 s |
| earlier runs in this session, group mid-rebalance with stale members | `docker stop` returned in **21.1 s** (idle) and **24.1 s** (loaded) |

Every measured stop is far inside the 660 s grace period, so a rollout does not have to
kill an in-flight simulation: it completes and the offset is acked before the consumer
closes. The two long stops both followed the removal of replicas with the group still
re-forming (stale members); **why that stop path took ~21-24 s is not investigated.** The
exact Spring Kafka listener `shutdownTimeout` default in this build was not measured, and
whether a record that never returns would be cut off is **not investigated**; PR-D5 makes
**no shutdown config change**.

## Reproduce

Environment (values mirror the repo `.env`):

```powershell
$env:JWT_SECRET="<from .env>"
$env:DB_HOST="localhost"; $env:DB_PORT="5432"; $env:DB_NAME="probity"
$env:DB_USERNAME="postgres"; $env:DB_PASSWORD="password"
$env:REDIS_HOST="localhost"; $env:REDIS_PORT="6379"; $env:REDIS_PASSWORD="password"; $env:REDIS_DATABASE="1"
$env:KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
$env:SPRING_DOCKER_COMPOSE_ENABLED="false"
$env:ANTHROPIC_API_KEY="pass"; $env:SENDGRID_API_KEY="pass"
$env:COOKIE_SECURE="false"; $env:SAME_SITE="Lax"; $env:PORT="8080"

docker compose up -d kafka postgres redis        # broker + store + cache
java -jar probity-api/target/probity-api-0.0.1-SNAPSHOT.jar   # the API, :8080
```

> The `password` above is the local compose Postgres/Redis password, and the seed's
> login is `admin` / `Password123!`. Both are **dev-only** and must never be used
> outside a local stack. The worker connects as the `probity_worker` role, whose
> password is provisioned out of band (Q7) and is never committed.

### The worker is a separate process since PR-D2

The API only queues requests: a simulation never reaches `COMPLETED` unless a
worker consumes `simulation-requested`. Start it alongside the API — either as a
jar (below), or via the compose profile: `docker compose --profile worker up -d`.

```powershell
$env:DB_HOST="localhost"; $env:DB_PORT="5432"; $env:DB_NAME="probity"
$env:DB_USERNAME="probity_worker"                  # least-privilege role, V18 grants
$env:DB_PASSWORD="<probity_worker role password>"  # set out of band when the role is created (Q7)
$env:KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
$env:SPRING_DOCKER_COMPOSE_ENABLED="false"
$env:WORKER_PORT="8081"
java -jar probity-worker/target/probity-worker-0.0.1-SNAPSHOT-exec.jar
```

Health: `http://localhost:8080/actuator/health` (API) and
`http://localhost:8081/actuator/health` (worker) both return `{"status":"UP"}`.

Seed one verified user + portfolio + positions + price bars, then read the
portfolio id:

```powershell
Get-Content loadtest/seed.sql -Raw | docker exec -i probity-postgres-1 psql -U postgres -d probity -v ON_ERROR_STOP=1
docker exec probity-postgres-1 psql -U postgres -d probity -t -c `
  "SELECT p.id FROM portfolios p JOIN users u ON u.id=p.user_id WHERE u.username='admin' LIMIT 1;"
```

Run the baseline. This is the recorded method — one warm-up + 3 runs each, reporting
medians and p95/p99 ranges. The `simulations` table is truncated before the warm-up
and before every measured run by default (`-ResetBetweenRuns`, ON), so no reset flag
is needed:

```powershell
./loadtest/run-baseline.ps1 -PortfolioId <portfolio-uuid>
```

Raw single runs are still possible (k6 ships as the `grafana/k6` image; the app runs
on the host, so the container reaches it via `host.docker.internal`):

```powershell
# Scenario A — concurrency 6, 60 simulations
Get-Content loadtest/k6/baseline.js -Raw |
  docker run --rm -i grafana/k6 run `
    -e BASE_URL=http://host.docker.internal:8080 `
    -e PORTFOLIO_ID=<portfolio-uuid> `
    -e VUS=6 -e ITERATIONS=60 -

# Scenario B — concurrency 1, 20 simulations
Get-Content loadtest/k6/baseline.js -Raw |
  docker run --rm -i grafana/k6 run `
    -e BASE_URL=http://host.docker.internal:8080 `
    -e PORTFOLIO_ID=<portfolio-uuid> `
    -e VUS=1 -e ITERATIONS=20 -
```

`baseline.js` defaults the login to `admin` / `Password123!` (overridable with
`-e USER` / `-e PASSWORD`). It aborts the run if any simulation fails
(`simulations_failed: count==0`, `abortOnFail`).

## Write-path investigation - writes per simulation and WAL under local Postgres config (2026-10-15)

Docs-only follow-up to PR-D5: **no `src`/test/`pom`/migration/`compose.yaml` change**. Two
questions — **how many statements write `result_payload` per simulation**, and **what a few
local Postgres WAL settings do to WAL per simulation and throughput** at 1 and 3 worker
replicas. Everything is **relative**: one dev laptop (32 logical cores, 31.6 GB RAM), shared
with Postgres/Kafka/Redis and the k6 client; these are not capacity figures. **No phase
decision changes** — Q4 (CPU-based HPA) is untouched and Q9 stays open.

### Writes per simulation (static + runtime)

**Static:** exactly **one** statement writes `result_payload` per simulation — the
`completeIfClaimHeld` bulk `UPDATE` in `SimulationRepository.complete`
(`SimulationJpaRepository`). `SimulationJpaEntity` marks `result_payload` and
`current_portfolio_value` `updatable = false`, so JPA `save()` / dirty-checking never writes
them; the claim path (`claimForProcessing`) writes only `status`/`updated_at`; the API's
PENDING insert writes them as `NULL`. `runAsync` is not `@Transactional`, so there is no
payload flush.

**Runtime:** with `log_statement = 'mod'`, one `run-async` simulation produced **1**
`insert into simulations (...)`, **2** `update simulations`
(`status=$1,updated_at=$2` for the claim, then the completion `UPDATE`), and the string
`result_payload` appeared in exactly **1** statement — the completion `UPDATE`. (Raw log
4,350,837 B; `log_statement` was reset to `none` and confirmed with `SHOW`.)

### Method

Same harness as PR-D5 — `loadtest/k6/baseline.js`, `TRUNCATE TABLE simulations` between
runs, observability stopped. Per condition: **1 warm-up** (discarded) + **3 measured
Scenario A runs** (6 VUs x 60 simulations) at **1** and **3** replicas (`probity-worker:prd5`
on `--network host`); standalone = 1 VU x 1 simulation. Units are bytes unless stated.

- WAL per run: `pg_current_wal_insert_lsn()` + `pg_wal_lsn_diff()` (exact),
  cross-checked against `pg_stat_wal.wal_bytes` after a >= 20 s settle.
- Full-page share: `Δpg_stat_wal.wal_fpi` x 8192 B / LSN-diff (unitless share of the WAL).
- Checkpoints: `pg_stat_bgwriter.checkpoints_req` / `checkpoints_timed` deltas per run.
- Throughput: k6 `simulations_completed.rate` (simulations/second), median and min-max over
  the 3 runs.

`wal_compression` and `synchronous_commit` apply to new sessions only, so the worker
replicas were **restarted** after each `ALTER SYSTEM` + `pg_reload_conf()` so pooled Hikari
connections pick them up (`max_wal_size`/`checkpoint_timeout` are `sighup` and apply live).

### Local Postgres config recorded before the experiment

Postgres **16.13** (`probity-postgres-1`); the `compose.yaml` postgres service has no
`command:`, so the image default entrypoint runs.

| setting | value | context |
|---|---|---|
| `max_wal_size` / `min_wal_size` | 1024 MB / 80 MB | sighup |
| `checkpoint_timeout` / `checkpoint_completion_target` | 300 s / 0.9 | sighup |
| `wal_compression` | off | superuser |
| `full_page_writes` | on | sighup |
| `synchronous_commit` | on | user |
| `shared_buffers` | 16384 x 8 kB (128 MB) | postmaster |
| `wal_level` | replica | postmaster |
| `fsync` | on | sighup |
| `log_statement` | none | superuser |

The four experiment GUCs were changed **local to this dev DB only** and restored at the end
(`ALTER SYSTEM RESET` + `pg_reload_conf()`; `SHOW` confirmed 1024 MB / 5 min / off / on, and
`postgresql.auto.conf` holds no experiment setting).

### Conditions

| condition | `max_wal_size` | `checkpoint_timeout` | `wal_compression` | `synchronous_commit` |
|---|---|---|---|---|
| c0 (recorded default) | 1024 MB | 300 s | off | on |
| c1 | 4096 MB | 900 s | off | on |
| c2 | 1024 MB | 300 s | pglz | on |
| c3 | 4096 MB | 900 s | pglz | on |
| cD - **diagnostic only, not a recommendation, durability reduced** | 4096 MB | 900 s | pglz | off |

### Standalone - one simulation

1 replica, dedicated pass (run before any bulk load):

| condition | WAL, LSN-diff (B) | `pg_stat_wal.wal_bytes` (B) | `wal_fpi` | checkpoints req/timed |
|---|---|---|---|---|
| c0 | 4,514,576 | 4,506,404 | 4 | 0 / 0 |
| c1 | 4,506,080 | 4,510,826 | 1 | 0 / 0 |
| c2 | 4,506,776 | 4,492,133 | 0 | 0 / 0 |
| c3 | 4,506,464 | 4,503,114 | 1 | 0 / 0 |
| cD | 4,505,712 | 4,491,069 | 0 | 0 / 0 |

One completion writes **≈ 4.51 MB in every condition** (matching PR-D5's 4,506,280 B); the
two methods agree within **0.4 %** and the full-page share is ≈ 0. So none of the knobs
change the standalone write — it is the row/TOAST rewrite, not checkpoint-driven. (At 3
replicas the LSN-diff standalone was 4,505,816-4,508,600 B across the five conditions.)
Method note: an earlier pass ran the warm-up first and read `pg_stat_wal` while a checkpoint
was still settling, which inflated the standalone `pg_stat_wal` figure (up to 21 MB); the
LSN-diff number was unaffected and the table above is the clean re-measure.

### Scenario A - 60 simulations

WAL per simulation = LSN-diff total / 60; throughput = k6 `simulations_completed.rate`.
Median (min-max) over 3 runs, unit MB/simulation and simulations/s. **1 replica:**

| condition | WAL/sim median (min-max), MB | full-page share (median) | `checkpoints_req` (3 runs) | throughput median (min-max), sims/s |
|---|---|---|---|---|
| c0 | **24.61** (22.85-25.91) | 0.77 | 8 | **1.896** (1.845-1.942) |
| c1 | 20.46 (19.11-21.06) | 0.65 | 2 | 1.840 (1.711-1.881) |
| c2 | 17.79 (16.72-17.93) | 0.93 | 6 | 1.598 (1.566-1.721) |
| c3 | 16.16 (16.13-17.90) | 0.70 | 1 | 1.645 (1.591-1.660) |
| cD | 16.16 (16.13-17.03) | 0.71 | 1 | 1.746 (1.701-1.775) |

**3 replicas:**

| condition | WAL/sim median (min-max), MB | full-page share (median) | `checkpoints_req` (3 runs) | throughput median (min-max), sims/s |
|---|---|---|---|---|
| c0 | **22.23** (19.85-24.96) | 0.72 | 7 | **3.276** (3.274-3.512) |
| c1 | 19.14 (15.65-21.46) | 0.66 | 2 | 3.251 (2.901-3.909) |
| c2 | 17.12 (9.97-17.56) | 0.87 | 5 | 2.753 (2.687-4.057) |
| c3 | 15.94 (9.44-16.77) | 0.70 | 1 | 2.892 (2.590-3.783) |
| cD | 16.16 (16.14-17.51) | 0.70 | 1 | 2.633 (2.441-2.828) |

`checkpoints_timed` was **0** in every run at both replica counts (a run is shorter than the
300 s / 900 s timeout), so every checkpoint above is a requested one. The `pg_stat_wal`
totals agreed with the LSN-diff totals within ≈ 3 % on every run.

### What the numbers say

- Under load, WAL per simulation is **3.5x-5.5x** the standalone write and **≈ 11x-17x** the
  1.47 MB stored payload (c0: 24.61 MB = 16.7x at 1 replica, 22.23 MB = 15.1x at 3) —
  consistent with PR-D5's 22.75 MB / 15x. Full-page images dominate (60-93 % of the WAL).
- Both knobs cut WAL per simulation. Relative to c0 (medians): `max_wal_size` +
  `checkpoint_timeout` alone **-17 %** (1 rep) / **-14 %** (3 rep); `wal_compression=pglz`
  alone **-28 %** / **-23 %**; both **-34 %** / **-28 %**. `checkpoints_req` falls from 7-8
  to 1-2 per 3 runs.
- **Throughput did not improve.** No condition beats c0, and where a difference exceeds the
  3-run spread it is *lower*: at 1 replica c2 (1.566-1.721) and cD (1.701-1.775) are below
  c0 (1.845-1.942); at 3 replicas the spreads overlap (cD 2.441-2.828 vs c0 3.274-3.512).
  Cutting WAL bytes did not raise throughput here; **where throughput actually binds at
  these replica counts is not investigated.**
- **cD (`synchronous_commit=off`)** — the labelled diagnostic, durability reduced — showed
  no throughput win outside the run spread at 3 replicas (2.441-2.828 vs c3 2.590-3.783) and
  only a marginal one at 1 replica (1.701-1.775 vs c3 1.591-1.660). It is **not a
  recommendation** and was not left enabled.
- Differences **within the run spread are not distinguishable**; the equal-or-lower
  throughput under compression (a possible compression/CPU effect) is **not investigated**
  here.

### Payload-write diagnostic - a no-payload worker build (throwaway, 2026-10-15)

Direct follow-up to the section above. If the completion `UPDATE` writes the ≈ 4.27 MB
payload, does **removing that single write** move throughput? A throwaway worker build
(branch `diag-no-payload`, **local only - never committed or pushed**) makes the completion
write a tiny placeholder, and **P0** (master build) is compared with **P1** (placeholder
build) at 1 and 3 worker replicas. **No `src`/test/`pom`/migration/`compose.yaml` change is
shipped; P1 is not a proposed design.** Everything is relative to the same dev laptop as
above (32 logical cores, 31.6 GB RAM); **no phase decision changes** - Q9 stays open and the
HPA (Q4) is untouched.

**The throwaway change (one call site, `SimulationRepository.complete`).** The Monte Carlo
computation, the claim `UPDATE`, `status`/`updated_at`/the claim columns and the client
contract are all unchanged; only the value written into `result_payload` by
`completeIfClaimHeld` changes:

```diff
- completeIfClaimHeld(id, internalId, payload, ...);
+ SimulationPayload placeholder = new SimulationPayload(
+         new SimulationPayload.Statistics(0, 0, 0, 0, 0),
+         new SimulationPayload.Outcomes(0, 0, 0, 0),
+         List.of(), List.of(), List.of());
+ completeIfClaimHeld(id, internalId, placeholder, ...);
```

A literal `'{}'::jsonb` placeholder was tried first and **breaks the harness unchanged**:
`SimulationMapper.toDto` (worker, run after the completion write) and
`SimulationController.mapToResponse` (api, the status body k6 polls) both dereference
`payload.statistics()`/`payload.outcomes()`, so an all-null payload NPEs the status endpoint
and k6 never observes `COMPLETED` (measured: P1 with the all-null placeholder completed
**0/60**, and ≈ 0 WAL). The placeholder is therefore made **structurally complete with zeros
and empty lists**: still tiny, **322 B** raw JSON / **424 B** stored `pg_column_size`,
against 4,270,978 B raw / 1,472,542 B stored for a real payload. `loadtest/k6/baseline.js`,
the api endpoints and the client-side work are **identical between conditions**; only the
worker image differs.

**Method.** Same harness as the section above: Scenario A (6 VUs × 60 iterations),
`TRUNCATE` before the warm-up and before each run, observability stopped, 1 warm-up + 3
measured runs per condition, medians with min-max. Postgres stays at the recorded **c0
defaults** (1024 MB / 300 s / `wal_compression=off` / `synchronous_commit=on`). Both
conditions use the **same P0 api jar** - only the worker image differs. Conditions are
block-interleaved in one session (P0 block, then P1 block, per replica count, back-to-back)
so any drift is shared. WAL is the LSN-diff (`pg_current_wal_insert_lsn` /
`pg_wal_lsn_diff`), reported per 60 simulations; `checkpoints_req` is from `pg_stat_bgwriter`.

**Results (Scenario A, 60 simulations).**

| condition | throughput median (min-max), sims/s | accept p95 / p99 median, ms | e2e p95 median (min-max), ms | e2e p99 median, ms | WAL/sim median, MB (LSN-diff) | `checkpoints_req` (3 runs) |
|---|---|---|---|---|---|---|
| P0 master, 1 replica | **1.915** (1.765-1.949) | 40.3 / 111.7 | 4,132.5 (3,915.1-4,622.6) | 4,612.2 | **23.60** | 3/2/3 |
| P1 no-payload, 1 replica | **20.219** (14.052-21.234) | 42.0 / 43.2 | 486.1 (279.0-671.1) | 487.0 | **0.0029** | 0/0/0 |
| P0 master, 3 replicas | **3.276** (2.750-3.470) | 53.8 / 442.4 | 3,416.1 (2,670.2-3,484.4) | 5,084.7 | **23.32** | 2/3/3 |
| P1 no-payload, 3 replicas | **18.151** (17.032-18.364) | 30.2 | 471.4 (449.0-646.0) | 652.8 | **0.0029** | 0/0/0 |

(accept p95/p99 are medians; the P0 3-replica accept p99 spread was 224.6-980.4 ms. WAL/sim
is the LSN-diff total ÷ 60; `checkpoints_req` is listed per run.)

**What the numbers say (and do not).**

- Removing the payload write raised Scenario A throughput **≈ 10.6x at 1 replica** (1.915 →
  20.219 sims/s) and **≈ 5.5x at 3 replicas** (3.276 → 18.151), cut e2e p95 **≈ 8.5x** at 1
  replica (4,132.5 → 486.1 ms) and **≈ 7.2x** at 3, and cut WAL per simulation from
  **≈ 23.6 MB to ≈ 0.003 MB** - two to three orders of magnitude. This is a wide gap,
  outside the run spreads.
- With the payload write removed, **1 and 3 replicas are not distinguishable** (20.219 vs
  18.151 median; the ranges 14.052-21.234 and 17.032-18.364 overlap). The extra replicas
  that helped P0 do not help P1 - **what binds P1 instead is not investigated** (api accept /
  the PENDING insert, the DB, Kafka, or the k6 client all remain candidates).
- P0 reproduces the section above: **1.915 vs 1.896** sims/s at 1 replica and **3.276 vs
  3.276** at 3, so the two sessions agree within the run spread.
- This is a **diagnostic**, not a ranking of storage designs and not a recommendation; it
  isolates the completion write by removing it, and nothing else.
- accept p95 barely moves between conditions (30-56 ms medians); the accept path (PENDING
  insert + broker write) was not the constraint measured here.

### Completion-write size for one simulation (simple check)

One completed simulation, on the real row and on a prepared copy row (a single
completion-shaped `UPDATE` measured by `pg_current_wal_insert_lsn` / `pg_wal_lsn_diff`,
re-writing the same `result_payload` value; the `result_payload` GIN index is maintained as
in a real completion):

| measurement | bytes |
|---|---|
| WAL of one completion-shaped `UPDATE` (LSN-diff) | 4,505,576 |
| `octet_length(result_payload::text)` (raw JSON) | 4,270,978 |
| `pg_column_size(result_payload)` (stored) | 1,472,542 |

Three figures for one simulation; no further claim is drawn from them here.

## Write cost vs response cost (2026-10-16)

**Question.** The *Payload-write diagnostic* above removed the completion payload write and
measured ≈ 10x throughput. That throwaway build changed **two** things at once: the write,
**and** the size of the status response the harness polls — a placeholder payload is also a
placeholder status body. This section separates them with a third condition that keeps the
**full write** and shrinks **only the response**.

**Conditions.** All three share the same harness, the same `loadtest/k6/baseline.js`, the
same accept path and the same Postgres config (c0 defaults); they differ only in two
local-only artifacts swapped in per condition. P0 and P1 are the two conditions of the
*Payload-write diagnostic* above; **P2 is new here**.

| id | completion write | `/status` COMPLETED body | api jar | worker image |
|---|---|---|---|---|
| **P0** master | full payload (≈ 4.27 MB raw / 1.47 MB stored) | full (≈ 3.90 MB) | `p0` | `probity-worker:p0` |
| **P1** no-payload | tiny structurally-complete placeholder (322 B raw / 424 B stored) | tiny (≈ 630 B) | `p0` | `probity-worker:p1` |
| **P2** full write, small response | full payload (as P0) | tiny (≈ 731 B) | `p2` | `probity-worker:p0` |

**The throwaway P2 change (one mapper, `SimulationApplicationService.mapToResult`).** The
Monte Carlo computation, the claim `UPDATE`, the completion `UPDATE` and the value written
into `result_payload` are all master's; only the **response** the worker's status mapper
builds shrinks. The api fat-jars this class and serves it from
`GET /api/v1/simulations/{id}/status` (the body k6 polls):

```diff
             .outcomes(SimulationStatusResponse.SimulationResponseOutcomes.builder()
                     ...
                     .build())
-            .allPaths(data.allPaths().stream().map(this::mapPath).toList())
-            .percentileSeries(data.percentileSeries().stream().map(this::mapPercentile).toList())
-            .distribution(data.distribution().stream().map(this::mapBucket).toList())
+            .allPaths(List.of())
+            .percentileSeries(List.of())
+            .distribution(List.of())
             .build();
```

The row is **still written in full**: `stored_bytes` is 88.3 MB per 60-simulation run for P2
(≈ 1.47 MB/sim), the same as P0. Only the bytes the client downloads on the COMPLETED poll
shrink (3.90 MB → ≈ 731 B). This is a control, not a proposed design.

**P1 also changed the response, not just the write.** The placeholder is what lands in
`result_payload`, so `GET .../status` reads it back and returns a tiny body (measured
≈ 630 B) — P1 alone therefore cannot say whether its ≈ 10x came from the write or from the
response. P1 still computes the **full** payload in memory before the write site replaces
it; `MonteCarloSimulationService.runAsync` builds it and passes it unchanged:

```java
225:  SimulationPayload payload = executeSimulationRun(new SimulationContext(positions, currentValue), params, numberOfSimulations, timeHorizonDays, confidenceLevel);
...
230:  Simulation completed = simulationRepository
231:          .complete(simulationId, currentValue, payload, fenceToken)
```

Only the argument handed to `complete(...)` differs — the placeholder in the P1 build, the
real `payload` on master (and in P2). So P1 removes the *write* cost and the *response* cost
together; **P2 removes only the response cost**.

**Method.** `loadtest/run-baseline.ps1` unchanged: Scenario A (6 VUs × 60 iterations),
`TRUNCATE` before the warm-up and before every measured run, observability stopped, one
warm-up + 3 measured runs per condition, medians with min-max. Three conditions at each of
**1 and 3 worker replicas**, all six blocks back-to-back in **one session** (P0, P1, P2 per
replica count), so any drift is shared. WAL is the LSN-diff (`pg_current_wal_insert_lsn` /
`pg_wal_lsn_diff`), reported per simulation; `checkpoints_req` is from `pg_stat_bgwriter`.
**api CPU** is a `Get-Process .CPU` time-delta (seconds of CPU = core-seconds) summed over
the api's `java` processes; **worker CPU** is a cgroup `cpu.stat usage_usec` delta summed
over the live worker containers. Both are for the whole run; divide by `wall_s` for cores.
The `/status` body size is one `curl -w '%{size_download}'` against a completed row.

**Results (Scenario A, 60 simulations).**

| condition | replicas | throughput median (min-max), sims/s | accept p95 / p99 median, ms | e2e p95 median (min-max), ms | e2e p99 median, ms | WAL/sim median, MB (LSN-diff) | `checkpoints_req` (3 runs) | api CPU s | worker CPU s | kafka lag after | `/status` bytes | stored bytes/sim |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| P0 master | 1 | **1.892** (1.811-1.928) | 35.1 / 41.2 | 4,376.1 (4,328.9-4,888.0) | 5,091.0 | **23.56** | 3/3/2 | 13.09 | 8.54 | 0 | 3,901,505 | 1.47 MB |
| P1 no-payload | 1 | **16.884** (16.373-18.735) | 39.1 / 42.4 | 647.0 (475.9-651.3) | 649.9 | **0.0029** | 0/0/0 | 1.09 | 1.13 | 0 | 630 | 424 B |
| **P2** full write, small response | 1 | **2.074** (2.064-2.105) | 49.5 / 64.6 | 3,821.1 (3,695.4-4,547.1) | 4,248.9 | **23.84** | 3/2/3 | 12.03 | 7.15 | 0 | 731 | 1.47 MB |
| P0 master | 3 | **3.272** (3.130-3.324) | 39.0 / 454.1 | 2,856.3 (2,812.7-3,240.1) | 3,643.6 | **22.11** | 2/3/2 | 12.88 | 9.15 | 0 | 3,901,916 | 1.47 MB |
| P1 no-payload | 3 | **17.335** (16.522-17.838) | 34.1 / 35.4 | 663.7 (662.5-667.0) | 672.0 | **0.0029** | 0/0/0 | 1.52 | 1.52 | 0 | 630 | 424 B |
| **P2** full write, small response | 3 | **3.689** (3.581-3.989) | 355.4 / 1,362.2 | 3,188.2 (2,145.5-3,212.4) | 4,090.7 | **22.80** | 2/3/3 | 16.16 | 9.52 | 0 | 731 | 1.47 MB |

(accept p95/p99 are medians over 3 runs; `checkpoints_req` is per run; api/worker CPU are
medians of the whole-run deltas; `kafka lag after` is the consumer-group lag immediately
after the run — **0** everywhere.)

**What the numbers say (and do not).**

- **P2 is closest to P0.** Keeping the full write and shrinking only the response leaves
  throughput where P0 is: **1.892 → 2.074 sims/s** at 1 replica and **3.272 → 3.689** at 3
  (a small **≈ +10 % / +13 %**; the P2 ranges 2.064-2.105 and 3.581-3.989 do not overlap the
  P0 ranges). WAL per simulation and stored bytes/sim are the same within spread
  (23.56 vs 23.84 and 22.11 vs 22.80 MB; both ≈ 1.47 MB/sim). **The response size is not
  what moved P1.**
- **P1 is the outlier.** **16.884** sims/s at 1 replica and **17.335** at 3 — **≈ 8.1x** P2
  at 1 replica and **≈ 4.7x** at 3 — with e2e p95 **≈ 647/664 ms** against P0's
  **≈ 4,376/2,856 ms**. The order-of-magnitude gap tracks the **write**: the only fast
  condition is the one that removes the write, and the condition that removes only the
  response is *not* fast. `checkpoints_req` says the same (7-8 per three runs for P0/P2,
  **0** for P1).
- **So the conclusion is P2 ≈ P0, and P1 is the outlier** — the earlier *Payload-write
  diagnostic*'s ≈ 10x survives as "removing the payload write is what moves it", but its
  attribution to the write **alone** rested on a confounded build; P2 is the control that
  separates the two. The response-size effect on its own is ≈ 10 %, an order of magnitude
  below the write effect.
- Adding replicas still helps P0 and P2 (≈ 1.7x / 1.8x from 1 → 3) and does **not** help P1
  (16.884 → 17.335, ranges overlap) — the same flat 1-vs-3 result the diagnostic recorded.
- The accept path is still not the differentiator here: accept p95 medians are 34-50 ms in
  five of the six blocks (P2 at 3 replicas is the exception, 355 ms p95 / 1,362 ms p99).

**What limits P1 at 3 replicas (measured, not inferred).** To put P1 under sustained load
well past Scenario A, the same P1 build ran 6 VUs × **300** iterations (~5x the work) at 3
replicas, sampled every ~4 s:

| candidate | measured during the probe | reading |
|---|---|---|
| **api** | **22.70 s** CPU over **21.6 s** wall = **≈ 1.05 cores** | the only process near a full core |
| worker (3 containers) | 11.12 s over 21.6 s = ≈ 0.51 cores | has headroom |
| Postgres | 0.14-6.3 % of one core | near idle |
| Kafka | 1.5-3.7 %, one 141.7 % spike | near idle |
| k6 client | 4.6-8.0 % of one core | near idle |
| host (32 cores) | 17-34 % busy (≈ 5.5-11 cores) | not saturated |
| consumer lag after | **0** | no backlog left |

300/300 iterations completed, 0 failed, `kafka_lag_after = 0`. Of the four candidates
(api CPU / k6 / Kafka lag / Postgres), **only the api shows a full core of use**; Kafka lag,
Postgres, Kafka and the k6 client were all near idle and the host was not saturated. Whether
the api is capped by raw CPU or by a serialisation point inside it is **not investigated**.
(At Scenario A's own load P1 at 3 replicas used only 1.52 s api CPU over 4.2 s ≈ 0.36 cores,
so the api reaches ≈ 1 core only under the ≈ 5x probe.)

**Caveat on condition order.** Within each replica block the conditions ran in the fixed
order P0, P1, P2, so the small P2-vs-P0 gap (≈ 10 %) shares any ordering/thermal drift this
session had; the ≈ 5-9x P1-vs-P0 gap is far outside it. The ≈ 10 % is reported as measured,
not as a mechanism.

## Caveats

- **k6 and the JVM share one laptop**, along with Postgres, Kafka and Redis. The load
  generator competes with the system under test for the same CPU and disk, which
  inflates and destabilises every latency here and caps the achievable throughput.
  These are ordering-of-magnitude figures for regression comparison, not capacity.
- Poll-based e2e inflates latency by the 200 ms status-poll granularity; prefer
  `server_latency_ms` to compare the worker pipeline — but remember it **excludes the
  completion `UPDATE`'s execute+commit** (measured 0.13–3.2 s here), so it is a lower
  bound, and `e2e_latency_ms` is the only metric that includes the payload write.
- The `simulations` table grows **≈ 7.89 MB per completed simulation** (bytes-per-row
  incl. TOAST + indexes; raw JSON ≈ 4.27 MB — see *Payload size (measured)*);
  `-ResetBetweenRuns` (default ON) truncates before the warm-up and before each run so
  payload writes do not dominate the measurement. Leave it off only when the growth is
  what you measure.
- Only the happy path is load-tested here; the park/seek and DLT paths are covered by
  `SimulationAsyncPartitionParkIntegrationTest` and
  `SimulationAsyncPoisonPayloadProductionConfigIntegrationTest`. Redelivery under
  listener `concurrency > 1` is not yet covered (see
  `SimulationAsyncUnclaimableConcurrencyPlaceholderTest`, to be implemented in PR-D1).
