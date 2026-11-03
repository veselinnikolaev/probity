# PR-D0 — Worker prerequisites (Phase 4)

> Draft PR description. Branch: `pr-d0-worker-prerequisites`. Nothing here is
> committed yet.

## Scope

PR-D0 delivers the **prerequisites only** for splitting the simulation worker into
its own deployable, per `docs/plans/PHASE_4_WORKER_EXTRACTION.md`. It makes the
topic, the delivery-timeout contract, and the failure paths safe to run under real
concurrency, and records the 1-replica "before" baseline. It deliberately keeps the
consumer at **concurrency 1** and does **not** introduce the separate worker image,
HPA, or a DB role split — those are PR-D1+.

Concretely, this PR:

- **Provisions 6 partitions** on `simulation-requested` (`probity.kafka.simulation-topic-partitions: 6`)
  so a later PR can raise listener concurrency without a reshuffle.
- **Aligns the delivery-timeout invariants**: `max.poll.interval.ms = 600000` (10m) and
  `probity.simulation.claim-lease = 12m`, so an in-flight simulation's lease always
  outlives the poll interval (`lease > max.poll.interval.ms ≥ slowest legitimate run`),
  enforced by a config-invariant test.
- **Replaces `nack()` in `handleUnclaimable`** with a per-record park/seek/requeue that
  pauses only the offending partition and resumes it after
  `probity.simulation.unclaimable-backoff` (default `1s`), instead of re-queuing the
  record to the front of the partition in a tight loop.
- **Wraps the value deserializer** in `ErrorHandlingDeserializer` (delegating to
  `JsonDeserializer`) so an undeserializable record is routed to the DLT instead of
  wedging the consumer.
- Adds the **k6 load harness** (`loadtest/`) and records the baseline in
  `docs/PHASE_4_MEASUREMENTS.md`.

### Files

| File | Change |
| --- | --- |
| `src/main/resources/application.yaml` | partitions `6`; `max.poll.interval.ms=600000`; `claim-lease=12m`; `unclaimable-backoff=1s`; `ErrorHandlingDeserializer` → `JsonDeserializer` |
| `src/main/java/.../config/SimulationProperties.java` | new `unclaimableBackoff` property |
| `src/main/java/.../event/SimulationRequestedEventConsumer.java` | `handleUnclaimable` now parks/seeks/resumes via the parker instead of `nack()` |
| `src/main/java/.../event/UnclaimablePartitionParker.java` | **new** — pause/resume/resolve for parked partitions |
| `src/test/resources/application-test.yaml` | test-profile overrides — pins `probity.kafka.simulation-topic-partitions: 1` so existing classes stay on a single partition while production defaults to 6 (the new per-partition tests opt into 2 via `@DynamicPropertySource`); Testcontainers Postgres + Redis config, Kafka listener `auto-startup: false`, test JWT/sendgrid values and logging levels |
| `src/test/java/.../async/AsyncKafkaTestSupport.java` | `registerAsyncBroker(...)` overload with a `wrapValueDeserializer` flag |
| `.gitignore` | ignore local run/build artifacts (`app-out.txt`, `build-out.txt`, `k6-*.txt`, …) |
| `loadtest/k6/baseline.js`, `loadtest/run-baseline.ps1`, `loadtest/README.md`, `loadtest/seed.sql` | **new** — reproducible baseline harness |
| `docs/PHASE_4_MEASUREMENTS.md` | **new** — recorded 1-replica baseline |
| `docs/plans/PHASE_4_WORKER_EXTRACTION.md` | design note; §2.7 carries the PR-D1 redelivery TODO |

## Tests

**6 new tests** (all green):

1. `SimulationWorkerConfigInvariantTest#claimLease_staysStrictlyAboveMaxPollInterval`
2. `SimulationWorkerConfigInvariantTest#valueDeserializer_isWrappedForDltRouting`
3. `SimulationWorkerConfigInvariantTest` — six partitions are provisioned
4. `SimulationWorkerConfigInvariantTest` — `unclaimableBackoff` is configured
5. `SimulationAsyncPartitionParkIntegrationTest#parkedPartition_doesNotStallSiblingPartition`
   — an unclaimable record parks only its own partition; a sibling partition keeps
   draining while the parked one backs off.
6. `SimulationAsyncPoisonPayloadProductionConfigIntegrationTest#poisonPayload_isDeadLetteredWithProductionDeserializerConfig`
   — a poison payload is dead-lettered using the **production** deserializer wrapping
   (not the test override).

Coverage gap left deliberately: redelivery of an unclaimable record under listener
`concurrency > 1` is **not** yet covered. It is captured as a `@Disabled` placeholder
(`SimulationAsyncUnclaimableConcurrencyPlaceholderTest`) and a TODO in
`PHASE_4_WORKER_EXTRACTION.md` §2.7, to be implemented in PR-D1 when concurrency is
actually raised.

## Build result

```
$ cmd /c "mvnw.cmd -o clean verify -B > build-out.txt 2> build-err.txt"
exit 0
[INFO] BUILD SUCCESS
[INFO] Total time:  03:38 min
[INFO] Tests run: 361, Failures: 0, Errors: 0, Skipped: 1
```

The one skipped test is the `@Disabled` PR-D1 placeholder. Existing tests were
extended (support helper overload), not changed.

## Baseline summary (1 replica, concurrency 1)

Recorded by `loadtest/run-baseline.ps1` — one discarded warm-up, then 3 runs each of
Scenario A (6 VUs × 60 sims) and Scenario B (1 VU × 20 sims), reported as
**median (min–max)** at **p95 and p99** over **two independent sessions**. The
`simulations` table is truncated before the warm-up and before every measured run
(`-ResetBetweenRuns`, default ON). Full detail and caveats live in
`docs/PHASE_4_MEASUREMENTS.md`.

The recorded baseline is the **best case** — every run starts on a freshly `TRUNCATE`d
table. It is not steady-state behaviour on a grown table: when the table was left
bloated (≈ 6 GB after ~1 000 sims), the completion `UPDATE` stalled for **2.6–3.2 s**
and throughput fell from ~1.14 to ~0.66 sims/s within a session (degradation case,
under *Payload size (measured)* / *Payload write cost* in the MEASUREMENTS doc).
**PR-D1 must beat the single-consumer ceiling of ~1.5–1.6 sims/s** (service time ≈
0.64 s per simulation), not the degraded figures.

Scenario A — concurrency 6:

| Metric | Session 1 | Session 2 |
| --- | --- | --- |
| throughput (sims/s) | 1.562 (1.552–1.569) | 1.606 (1.498–1.613) |
| accept p95 (ms) | 50.3 (27.6–55.2) | 36.4 (24.2–58.3) |
| accept p99 (ms) | 59.6 (48.7–63.5) | 95.2 (38.7–102.3) |
| e2e p95 (ms) | 5375.1 (5043.4–6761.1) | 5135.1 (4762.8–6076.5) |
| e2e p99 (ms) | 5664.7 (5526.8–7299.0) | 5424.3 (5249.2–6641.2) |
| server p95 (ms) | 3913.2 (3697.1–5449.4) | 4145.3 (3433.2–4972.7) |
| server p99 (ms) | 4619.7 (4338.5–6213.6) | 4220.9 (4092.0–5453.0) |

Scenario B — concurrency 1:

| Metric | Session 1 | Session 2 |
| --- | --- | --- |
| throughput (sims/s) | 0.863 (0.839–0.881) | 0.872 (0.864–0.894) |
| accept p95 (ms) | 35.8 (28.0–54.7) | 37.7 (37.1–54.5) |
| accept p99 (ms) | 62.4 (42.4–65.3) | 53.8 (48.3–61.3) |
| e2e p95 (ms) | 1356.2 (1293.6–1386.3) | 1324.2 (1323.1–1343.2) |
| e2e p99 (ms) | 1373.6 (1317.1–1390.1) | 1327.2 (1324.6–1421.4) |
| server p95 (ms) | 50.1 (43.7–52.8) | 34.1 (32.2–35.2) |
| server p99 (ms) | 68.7 (66.8–79.4) | 35.6 (34.4–37.4) |

- `simulations_completed` 100 %, `simulations_failed` 0, `http_req_failed` 0 in every run.
- **Scenario B is completion-write bound, not poll-bound and not worker CPU:**
  `server_latency_ms` p95 (updatedAt − createdAt) is ~34–50 ms, but `updatedAt` is
  stamped *before* the completion `UPDATE`, whose execute+commit was logged at
  **0.13–3.2 s** on this host when the table was dirty (looks bound by Postgres
  WAL/disk I/O; no lock waits logged — autovacuum and checkpoint contention were not
  ruled out).
  With `-ResetBetweenRuns` the slow completions disappear and B's e2e p95 drops
  ~3.6 s → **~1.35 s** (≈ accept + 0.4 s poll floor + 0.5 s final payload download).
  See the trace in the MEASUREMENTS doc.
- **With per-run resets, Scenario A no longer degrades within a session** (degradation
  case: ≈1.14 → 0.80 → 0.66 sims/s without resets; now flat at 1.552–1.569 /
  1.498–1.613) — each completed simulation persists the full result payload (raw JSON
  ≈ 4.27 MB; stored jsonb ≈ 1.47 MB/row, ≈ 7.89 MB bytes-per-row incl. TOAST +
  indexes), so `-ResetBetweenRuns` (default ON) keeps table growth from dominating
  later runs.
- Two sessions agree closely (A median 1.562 vs 1.606; B 0.863 vs 0.872), so the
  *method* is reproducible; absolute values are host-load dependent (an earlier session
  measured A near 2 sims/s on the same laptop).

## Environment notes

- Dev DB had a stale Flyway V17 checksum from a previously built local image; no SQL change in this PR. Fixed by recreating the dev DB.
- Flyway repair should only be used after confirming the target column nullability matches V17.

Additional measurement notes: k6 and the JVM share one laptop (k6 contends with Postgres,
Kafka, Redis and the app for CPU and disk); the Grafana/Prometheus/Loki containers were
stopped before recording; and `run-baseline.ps1` truncates `simulations` before the
warm-up and before every measured run (`-ResetBetweenRuns`, default ON) because payload
writes otherwise dominate the measurement.
