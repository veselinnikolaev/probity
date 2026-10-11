# loadtest

k6 harness for the async simulation worker pipeline. Introduced by **PR-D0**
(Phase 4 §2.9) to record the 1-replica baseline in
[`docs/PHASE_4_MEASUREMENTS.md`](../docs/PHASE_4_MEASUREMENTS.md).

## Layout

| File | Purpose |
| --- | --- |
| `k6/baseline.js` | End-to-end runner: CSRF → login → `POST /api/v1/simulations/run-async` → poll status to `COMPLETED`. Emits `accept_latency_ms`, `e2e_latency_ms`, `server_latency_ms`, `status_polls`, `simulations_completed`, `simulations_failed`. |
| `run-baseline.ps1` | Reproducible driver: 1 discarded warm-up run, then N runs each of Scenario A (VUs=6, 60 iter) and Scenario B (VUs=1, 20 iter); prints the median and min–max of throughput and of accept/e2e/server latency at p95 **and** p99, and TRUNCATEs `simulations` before the warm-up and before every measured run (`-ResetBetweenRuns`, default ON). See **Reproducible baseline** below. |
| `seed.sql` | Idempotent seed: verified `admin` user, one portfolio, AAPL/GOOGL positions, and 31 days of price bars (closes the on-read sync window so requests never call out to Yahoo Finance). |

## Prerequisites

- The compose stack: `docker compose up -d kafka postgres redis`
- One app replica built from the branch under test, e.g.
  `java -jar target/Probity-0.0.1-SNAPSHOT.jar` (see the env block in the
  measurements doc).
- The `grafana/k6` image: `docker pull grafana/k6`

## Run

```powershell
# 1. Seed (after the app has run once so Flyway V7 has inserted the assets)
Get-Content loadtest/seed.sql -Raw |
  docker exec -i probity-postgres-1 psql -U postgres -d probity -v ON_ERROR_STOP=1

# 2. Grab the portfolio id
docker exec probity-postgres-1 psql -U postgres -d probity -t -c `
  "SELECT p.id FROM portfolios p JOIN users u ON u.id=p.user_id WHERE u.username='admin' LIMIT 1;"

# 3. Run
Get-Content loadtest/k6/baseline.js -Raw |
  docker run --rm -i grafana/k6 run `
    -e BASE_URL=http://host.docker.internal:8080 `
    -e PORTFOLIO_ID=<portfolio uuid> `
    -e VUS=6 -e ITERATIONS=60 -
```

The app listens on the **host**; the k6 container reaches it via
`host.docker.internal` (Docker Desktop). If k6 runs directly on the host, use
`-e BASE_URL=http://localhost:8080`.

## Reproducible baseline

A single k6 run is noisy, so the numbers in
[`docs/PHASE_4_MEASUREMENTS.md`](../docs/PHASE_4_MEASUREMENTS.md) are produced by
`run-baseline.ps1`, which does **1 discarded warm-up run** followed by **3 runs
each** of Scenario A (VUs=6, 60 iterations) and Scenario B (VUs=1, 20
iterations), then prints the **median** and **min–max** of throughput and of
accept/e2e/server latency at **p95 and p99**. The `simulations` table is
`TRUNCATE`d before the warm-up **and before every measured run**
(`-ResetBetweenRuns`, default ON), so runs start from a ~empty table instead of
competing with rows from earlier runs.

```powershell
# With the compose stack and one app replica already running (see above):
./loadtest/run-baseline.ps1 -PortfolioId <portfolio-uuid>
```

Optional parameters: `-BaseUrl`, `-User`, `-Password`, `-K6Image`, `-Runs`,
`-VusA`/`-IterationsA`, `-VusB`/`-IterationsB`, `-SkipWarmup`,
`-ResetBetweenRuns` (default ON; pass `-ResetBetweenRuns:$false` to keep rows
between runs, e.g. for a growth test; `-ResetSimulations` still works as an
alias for it).

Each run also exports a k6 JSON summary (via `--summary-export`, deprecated in
newer k6 releases but still working with the image recorded in the measurements
doc — `grafana/k6` v2.3.0) into a temp directory, so `server_latency_ms` and all
percentiles can be re-inspected without re-running the load. The script prints
that directory on exit.

> **Why the per-run reset is on by default.** Every `COMPLETED` simulation stores
> a multi-MB `result_payload` (all Monte Carlo paths), so the `simulations` table
> grows by gigabytes across repeated runs. A bloated table can stall the worker's
> completion `UPDATE` on WAL I/O, which makes a simulation miss k6's poll ceiling
> and trips the `simulations_failed` threshold. With `-ResetBetweenRuns` (default
> ON) each measured run starts clean; leave it off only when the growth itself is
> what you are measuring.

> Both the script's `-User`/`-Password` defaults (`admin` / `Password123!`) and
> the `password` used by the compose Postgres/Redis are **dev-only** credentials
> and must never be used outside a local stack.

## Knobs

| Env var | Default | Meaning |
| --- | --- | --- |
| `BASE_URL` | `http://host.docker.internal:8080` | App base URL |
| `PORTFOLIO_ID` | — (required) | Seeded portfolio to simulate |
| `USER` / `PASSWORD` | `admin` / `Password123!` | Login credentials — **dev-only** |
| `VUS` | `6` | Virtual users (concurrency) |
| `ITERATIONS` | `60` | Total simulations (shared across VUs) |

## Notes

- Login is rate-limited (a handful of attempts per minute), so the script logs in
  **once** in `setup()` and reuses the access token / CSRF token across VUs.
- Each iteration sends a fresh `Idempotency-Key`, so every iteration is a distinct
  simulation rather than a replay of a cached `202`.
- The run **aborts** if any simulation does not reach `COMPLETED`
  (`simulations_failed: count==0`, `abortOnFail`).
- `server_latency_ms` is the server-side pipeline time taken straight from the
  `COMPLETED` status payload (`updatedAt − createdAt`) — it carries **no**
  client poll floor. `e2e_latency_ms` still includes the 200 ms status-poll
  floor, kept for continuity; prefer `server_latency_ms` when comparing worker
  throughput. See the caveats in the measurements doc.
