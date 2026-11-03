# PR-D5 — docs + measurements (Phase 4, §2.8 / §2.8.1)

Docs-only PR. No `src`, test, `pom.xml`, migration or `compose.yaml` change.

## What this closes

- **§2.8 — N-replica numbers, p95/p99, consumer lag, rolling-restart result.** New
  section in `docs/PHASE_4_MEASUREMENTS.md`: *N-replica scaling, rolling restart, and the
  §2.8.1 WAL re-check (PR-D5 build, 2026-10-15)*.
- **§2.8.1 — the `pg_stat_wal` re-check** PR-D4 left open before the CPU-based HPA can be
  relied on.
- **PR-D4's graceful-shutdown "not investigated" gap** — verified (no code change).

## Headline numbers (one dev laptop: 32 logical cores, 31.6 GB RAM, shared with Postgres/Kafka/Redis; relative, not capacity)

| scenario | 1 replica | 3 replicas | 6 replicas |
|---|---|---|---|
| A — 6 VUs x 60 sims (sims/s, median) | 1.724 | 3.155 (**1.83x**) | 3.770 (**1.19x**) |
| A — accept p95 / p99 (ms) | 43.1 / 51.0 | 47.5 / 892.0 | 69.4 / 1084.2 |
| A — e2e p95 / p99 (ms) | 4612.6 / 5506.0 | 4230.8 / 5149.8 | 2388.1 / 4085.1 |
| A — server p95 / p99 (ms) | 3592.6 / 4576.3 | 3254.5 / 4060.5 | 1570.3 / 1775.0 |
| B — 1 VU x 20 sims (sims/s, median) | 1.079 | 1.049 | 1.036 |

- Scaling is **sub-linear** (1.83x then 1.19x). The **6-partition ceiling was never
  reached** (at 6 replicas each worker owned exactly one partition). Consumer lag was 0 at
  the settled end of every run.
- The plateau is **not a k6 VU ceiling**: an 18-VU probe at 6 replicas gave 2.97 sims/s
  (lower than the 3.77 sims/s at 6 VUs); at 1 replica, 1.63 sims/s.
- `accept_latency_ms` p99 degrades monotonically as replicas grow (51 → 892 → 1084 ms);
  e2e/server improve. The binding resource is shared and server-side — **not investigated**
  further.
- **§2.8.1:** WAL per simulation is **4.51 MB vs 1.47 MB stored payload = 3.06x**
  standalone (LSN-diff and `pg_stat_wal` agree within 0.7 %), and **22.75 MB = 15x** under
  a sustained 60-simulation run (≈73 % of it full-page images, `checkpoints_req` +2). The
  "the cost is *how the row is written*, not the hardware" reading is **confirmed**, so the
  Q4 CPU-HPA caution **stands** (`checkpoint_timeout=300 s`, `max_wal_size=1024 MB`,
  `full_page_writes=on`, `wal_compression=off`).
- **Rolling restart under load:** 200/200 `COMPLETED`, **zero** DLT records, **zero** park
  events, no row left `PENDING` with no claim; 6/200 responses exceeded the harness's
  150-poll budget and completed afterwards (latency, not loss). Replacement replica owned
  all 6 partitions again 11.1 s after start.
- **In-flight SIGTERM (deterministic):** the record was captured mid-run and completed on
  the same replica before the consumer closed — `Received` 02:20:35.125 → `Completed`
  02:20:35.658 → `Consumer stopped` 02:20:35.668, exit 143, `docker stop` returned in
  0.6 s. So a graceful `SIGTERM` finishes the in-flight simulation and acks its offset; the
  park/seek + 12 m claim lease stays the backstop for an *ungraceful* kill.

## Verification

- `mvnw.cmd -o clean verify -B` → exit 0, BUILD SUCCESS (4:09), **365 run, 1 skipped**
  (350 api + 15 worker) — unchanged (docs-only).
- `probity-worker:prd5` built and run at 1, 3 and 6 replicas against the compose stack;
  `/actuator/health` UP on every instance, all 6 partitions owned, one `run-async` to
  `COMPLETED`; measurement processes stopped (real PID + `docker stop`).

## Follow-ups (recorded, not fixed here)

1. **Dev-stack: containers on the compose bridge cannot consume this Kafka.** The broker
   advertises `PLAINTEXT://localhost:9092`, so a bridge-network consumer resolves the
   advertised address to itself and never joins the group. Measurements therefore used
   `--network host` replicas. Candidate fix: a second Kafka listener
   (`PLAINTEXT_HOST` + `PLAINTEXT_INTERNAL`) and advertising the compose service name for
   the internal listener — pre-existing, not PR-D5's to change.
2. **Sub-linear replica scaling / WAL amplification.** Root cause (checkpoint/WAL I/O on
   the shared payload write vs. something else) **not investigated**. Candidates: write the
   payload once / in chunks, `wal_compression=on`, tune `checkpoint_timeout`/`max_wal_size`,
   or Q9 (store a summary + percentiles, not all paths).
3. **Q4 CPU-based HPA.** Concern stands; unchanged in this PR. If the write is the
   constraint, a CPU signal may saturate late or not at all — reconcile before relying on it.
4. **Graceful-shutdown timeouts.** PR-D5 measured the behaviour (in-flight record completes;
   every settled stop 0.6 s) but did **not** measure the Spring Kafka listener
   `shutdownTimeout` / Boot shutdown-phase defaults, and made no config change. Two earlier
   stops in this session took 21.1 s (idle) and 24.1 s (loaded) when the group was
   mid-rebalance with stale members — cause not investigated.
5. **`.env` alone does not start the API** for a measurement run: the mail transport needs
   `SENDGRID_API_KEY` at startup even when no mail is sent, and `.env` has none. The
   recorded startup block in `docs/PHASE_4_MEASUREMENTS.md` **does** set
   `SENDGRID_API_KEY`/`ANTHROPIC_API_KEY` explicitly, so no doc change; recording it here so
   a future run does not trip on it.

## Method notes worth keeping

- `pg_stat_wal` does not flush with the write: an immediate post-write read returned 0.
  Read it after a **≥ 20 s settle**, or use `pg_current_wal_insert_lsn()` +
  `pg_wal_lsn_diff()` (what the section does, and cross-checks).
- The recorded `baseline.js` harness aborts the whole run on the first non-terminal
  simulation (`abortOnFail`). The rolling-restart loads were run with `--no-thresholds`,
  because a restart deliberately interrupts clients and the abort would truncate the load.
- `docker stop` on a worker container is a real SIGTERM to the JVM (host networking), which
  is what the k8s `terminationGracePeriodSeconds: 660` path produces.
