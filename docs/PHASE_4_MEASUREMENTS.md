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

## Topology under test

| Item | Value |
| --- | --- |
| App replicas | **1** (single JVM host process, `java -jar target/Probity-0.0.1-SNAPSHOT.jar`) |
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
java -jar target/Probity-0.0.1-SNAPSHOT.jar      # 1 replica of the PR-D0 build
```

> The `password` above is the local compose Postgres/Redis password, and the seed's
> login is `admin` / `Password123!`. Both are **dev-only** and must never be used
> outside a local stack.

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
