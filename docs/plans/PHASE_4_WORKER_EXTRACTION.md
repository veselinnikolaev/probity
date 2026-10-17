# Phase 4 — Extracting the async simulation worker into a separately deployable unit

**Status:** design note for review. Prose and signatures only — no implementation code, no code changes.
**Progress:** PR-D0, PR-D1 and PR-D2 are merged to `master`; so are PR-D3 (PR #13), PR-D4 (PR #14) and the post-D4 compose/`.dockerignore` fix (PR #15). **PR-D5 is this PR (#16, open) and is docs-only** (no `src`, test, `pom`, migration or `compose.yaml` change). Decisions Q1-Q8 below are unchanged; **Q9** remains open (the §2.8.1 re-check in `docs/PHASE_4_MEASUREMENTS.md` confirms the payload-write cost and keeps the Q4 CPU-signal caution).
**Location:** `docs/plans/` did not exist; this note creates it. Existing phase notes live flat in `docs/` (`PHASE_2_IMPLEMENTATION_NOTES.md`, `TECHNICAL_REPORT.md`); move this file if you prefer a flat layout.

---

## Decisions

Recorded, no longer open:

- **Q1 — data access:** accept the read-only schema coupling now (**Option B**, §2.2). Payload enrichment stays a **deferred fast-follow**.
- **Q2 — partitions:** **6**.
- **Q3 — image:** **separate worker image**.
- **Q4 — HPA:** **CPU-based HPA now**; KEDA is a later option.
- **Q6 — invariant:** confirmed — **lease 12m > max.poll.interval.ms 10m ≥ slowest legitimate run**.

Still open (see §2.10): **Q5, Q7, Q8, Q9**.

---

## Step 0 — Precondition build

**Invocation (as specified):** `Start-Process -FilePath ".\mvnw.cmd" -ArgumentList "-o","clean","verify","-B" -Wait -PassThru -NoNewWindow -RedirectStandardOutput "build-out.txt" -RedirectStandardError "build-err.txt"`. The process handle returned **`EXITCODE=0`**. The run's logs are **kept** as evidence at `build-out.txt` and `build-err.txt` (repo root), excluded from git via `.git/info/exclude` so the tree stays clean.

| Item | Result |
|---|---|
| Exit code | **0** |
| Build line | `[INFO] BUILD SUCCESS` |
| Total time | `03:33 min` |
| Surefire (`target/surefire-reports`) | 35 files — **354 tests, 0 failures, 0 errors, 0 skipped** |
| Failsafe (`target/failsafe-reports`) | directory absent — runs nothing (every `*IntegrationTest` ends in `Test`, runs under Surefire) |

Green (matches `TECHNICAL_REPORT.md` §6.1: 354/354). Proceeding.

---

## 1. What the worker actually needs — code and data

Traced from `SimulationRequestedEventConsumer.consume` down through `MonteCarloSimulationService.runAsync`.

**Classes:** listener `SimulationRequestedEventConsumer`; claim/state `SimulationRepository`, `SimulationJpaRepository`, `SimulationJpaEntity`, `SimulationMapper` (fenced `claimForProcessing`/`complete`/`releaseToPending`/`markFailedIfPending`, `findLiveStatusById`); app service `SimulationPort`, `SimulationApplicationService.executeAsync`; compute `MonteCarloSimulationService.runAsync`; portfolio read `PortfolioQueryPort.loadPortfolioWithPositionsAndAssets` → `PortfolioQueryService`; valuation `PortfolioValuationService`, `PortfolioPriceFetcher`; market data `MarketDataPort`, `MarketDataService`, `PriceBarRepository`; risk `RiskPort`, `RiskCalculator`; config `TradingConfiguration`, `SimulationProperties`, `DataUtil`, `DateRangeUtil`; events `DomainEventPublisher`, `SimulationCompletedEvent`/`SimulationFailedEvent`; Kafka infra `KafkaConsumerConfig`, `KafkaConfig`, `SimulationFailedRecordRecoverer`.

**Not needed:** `bff`, `security`, `web`, `notification`/SendGrid, `assistant`/Spring AI, rate limiting, and `CacheConfiguration` — the portfolio query path is not `@Cacheable`, so **the worker has no Redis dependency**.

| Table | Consumer | `runAsync` | DLT recoverer | Access |
|---|---|---|---|---|
| `simulations` | claim + `findLiveStatus` | fenced `complete` | `markFailedIfPending` | **read + write** |
| `portfolios`, `positions`, `assets` | — | `loadPortfolioWithPositionsAndAssets` | — | **read** |
| `price_bars` | — | `getHistoricalBars` **iff** no return+vol override | — | **read** |
| Redis | — | none | — | none |

**Payload vs DB reads (the key finding).** `SimulationRequestedEvent` carries `simulationId, portfolioId, userId, numberOfSimulations, timeHorizonDays, confidenceLevel, assumedReturnPercent, assumedVolatilityPercent, marketDataSnapshot, occurredAt`. The snapshot removes the live-price read **only when it is non-empty** (`runAsync` falls back to live prices otherwise), it does **not** remove the portfolio read — asset identity (ticker per position) was never added to the payload, so `portfolios → positions → assets` are loaded every run — and it does **not** remove the `price_bars` read unless both return/vol overrides are supplied. This drives item 2.

---

## 2. Design decisions

### 2.1 Module layout — Maven multi-module (api, worker, shared kernel)

Convert `Probity` into a parent POM with three modules. `probity-shared` holds the shared kernel — the event contract, `SimulationStatus`, the simulation domain value type, and the `simulations` persistence adapter (entity, mapper, Spring Data repo, fenced updates) — so the claim/complete SQL cannot drift between consumers. `probity-api` is today's application (all non-worker code). `probity-worker` is a new `@SpringBootApplication` (listener, `executeAsync`, `MonteCarloSimulationService`, risk config) with its own jar and image. This is the least disruptive shape that yields an independently deployable jar + image and can be staged.

- *Rejected — separate repo:* loses atomic cross-cutting refactors, duplicates CI/release tooling, invites schema/contract skew.
- *Rejected — second `@SpringBootApplication` in the same module:* one classpath, one Flyway owner, one fat jar; cannot version/scale the worker independently.

### 2.2 Worker data access — shared Postgres; write only on `simulations`, read elsewhere

The worker gets a dedicated DB role: `SELECT` on `simulations` and `UPDATE` on `simulations` (the claim/complete/release/mark-failed writes), `SELECT` on `portfolios`, `positions`, `assets`, `price_bars`, and `SELECT` on `flyway_schema_history` (required for `ddl-auto=validate`). No insert/delete. This needs no change to the event or producer and keeps claim semantics identical.

The role is created **out of band** — by ops/DBA, or by a secret-managed provisioning step — **never in the migration**; the password is never in the migration either. The grants are provisioned by an **api-owned Flyway migration** (`db/migration/V<next>__worker_role_grants.sql`). **It must be guarded and idempotent:** the worker role is not guaranteed to exist in every environment, and roles are cluster-level, so the migration **skips the grants when the role is absent** (no unconditional `GRANT`, which would fail the api's startup) and re-runs without error. **Ordering dependency:** the out-of-band role creation must precede the grant migration in an environment; record this as a dependency of PR-D2 (§2.9).

- *Honest caveat:* this leaves the worker coupled to the monolith schema for the portfolio read, and `price_bars` is still hit on the derive-parameters path — acceptable for a first extraction and reversible.
- *Fast-follow (not a prerequisite):* enrich the payload with the resolved position snapshot (`ticker, quantity, assetId`) and optionally pre-derived `dailyReturn/dailyVolatility`, making the worker write-only to `simulations`. Deferred because deriving parameters at request time would move the historical-bar fetch onto the `202 Accepted` path. **Q1 resolved: deferred fast-follow, not part of this phase.**
- *Rejected — everything in the payload:* changes the request path and the event contract in the same PR as the extraction.
- *Rejected — separate database:* splits the single transactional boundary the claim fence depends on.

### 2.3 Monolith changes — disable the listener by property; sync path untouched

Gate the consumer bean with `@ConditionalOnProperty(name = "probity.simulation.worker.enabled", matchIfMissing = true)` so the monolith simply does not start it. Deploy the api with the flag `false` (or a `monolith` profile) and the worker with it `true`. The producer (`SimulationEventPublisher`) stays in the api as the hand-off; `KafkaConsumerConfig` and the DLT recoverer (which references `SimulationRepository`) are gated the same way if the consumer config is to leave the monolith entirely. The synchronous path (`MonteCarloSimulationService.run`, `POST /simulations/run`) never touches the listener and is unchanged.

- *Rejected — delete the listener from the api at build time:* forces lockstep shipping and turns rollback into a rebuild instead of a flag flip.

### 2.4 Flyway ownership — the API stays the single migration owner

`probity-api` keeps `spring.flyway.enabled=true` and owns `db/migration`; the worker runs `spring.flyway.enabled=false` with `ddl-auto=validate`. The worker role/grant migration from §2.2 (`V<next>__worker_role_grants.sql`) is a normal entry in the api's history and must be guarded/idempotent (roles are cluster-level; it skips the grants when the role is absent, and never contains the role password — the role itself is created out of band). *Rejected:* worker owning a subset of migrations (two writers race `flyway_schema_history`) and a dedicated migration module run by a job (extra machinery, no benefit at this scale).

### 2.5 Kafka consumer config for scaling

| Knob | Today | Recommendation |
|---|---|---|
| Topic partitions (`probity.kafka.simulation-topic-partitions`) | `1` | **6–12** (pick **6**). Cannot be *decreased* later without topic recreation. |
| Listener concurrency | none (1) | `spring.kafka.listener.concurrency`, start 1, raise with replicas |
| `max.poll.records` | 1 | keep 1 |
| `max.poll.interval.ms` | 300000 (5m) | **600000 (10m)** |
| `claim-lease` | 5m | **12m** |
| `ErrorHandlingDeserializer` | bare `JsonDeserializer` in `application.yaml` | wrap `JsonDeserializer` for prod parity with the test slice |

**Invariant: `lease (12m) > max.poll.interval.ms (10m) ≥ slowest legitimate run`.** The claim must outlive the poll interval: if the lease expired first, a second worker could claim the stale `PROCESSING` row and re-run the same CPU-heavy simulation while the first is still alive — the fenced `complete` protects the data write but not the wasted compute or the double load. Keeping the poll interval at least as long as the slowest run prevents a healthy-but-slow consumer from being evicted and its record redelivered. *Heartbeat renewal rejected for now:* it adds a background writer and a new failure mode, and is unnecessary while one record per poll is the model.

**`nack()` replaces all-partition pause.** Today `handleUnclaimable` calls `ack.nack(NACK_BACKOFF)`, which pauses **all** partitions the container owns — head-of-line blocking across every partition. Replace it with **per-partition** handling: `pausePartition(TopicPartition)` / `resumePartition(TopicPartition)` (Spring Kafka 2.7+), or `consumer.pause(tp)` via a `ConsumerAwareMessageListener`, and on resume **seek that partition back to the record's offset** so the same record is re-delivered after the backoff (its row is claimable once the lease lapses). With `concurrency > 1`, each child `MessageListenerContainer` owns its own partitions, so pause/resume must be issued to **the child container that owns the partition** (resolve it from the container registry by the record's topic/partition), not the parent. This is a prerequisite (PR-D0), not part of the extraction.

### 2.6 Packaging

**Dockerfile:** reuse the two-stage build and add a worker image (`Dockerfile.worker`) with its own entrypoint and no traffic port (actuator only); independent tags (`probity:latest`, `probity-worker:latest`) are preferred over one image with two entrypoints.

**docker-compose:** add a `simulation-worker` service with no host port, setting the worker-enabled flag `true`, Flyway disabled, the worker DB role credentials, `KAFKA_BOOTSTRAP_SERVERS`, `SIMULATION_CONSUMER_GROUP`, and `SIMULATION_CLAIM_LEASE=12m`; depend on healthy kafka + postgres.

**Kubernetes** (new `k8s/` manifests): a Deployment (start 2 replicas, `RollingUpdate`, `terminationGracePeriodSeconds` set from the invariant so a scale-down or rollout never kills an in-flight simulation that the lease would then have to recover — at minimum `max.poll.interval.ms` (600s), and use a **margin above it, 660s**, because the grace period must outlast the poll window plus the last poll's processing, not merely equal it), a ClusterIP Service for the actuator/metrics port only, liveness/readiness probes at `/actuator/health/liveness` and `/actuator/health/readiness` (enable `management.endpoint.health.probes.enabled`), resources requests `500m`/`1Gi` and limits `2`/`2Gi` (tune after load testing), and an `autoscaling/v2` HPA targeting CPU 70%. CPU HPA is what is realistic without extra operators (metrics-server only) and tracks load reasonably for a CPU-bound workload; Kafka-lag HPA needs KEDA or a Prometheus adapter and is an optional follow-up. Set **`maxReplicas = partitions ÷ concurrency-per-pod`**, not the partition count — replicas beyond that just idle with no partitions to own.

### 2.7 Tests

Run the async suite (`me.veselin.probity.simulation.async`, 6 classes + `BaseAsyncSimulationIntegrationTest` + `AsyncKafkaTestSupport`) against the worker module, booting the worker slice on Testcontainers Postgres/Kafka. Add a test that the monolith no longer consumes: boot the api slice with the worker flag `false`, publish a `SimulationRequestedEvent`, and assert the consumer bean/container is absent and the row stays `PENDING` with no worker consumer group formed. Keep `ArchitectureTest` and add a worker rule forbidding dependencies on `..bff..`, `..security..`, `..web..`.

**TODO (PR-D1).** PR-D0 verifies per-partition parking only at concurrency 1 (`SimulationAsyncPartitionParkIntegrationTest`); the `@Disabled` `SimulationAsyncUnclaimableConcurrencyPlaceholderTest` marks the gap. Once PR-D1/D2 raise `spring.kafka.listener.concurrency` or run multiple replicas, implement it: an unclaimable record must park only the partition of the child container that owns it, leave sibling children's partitions flowing, and issue the seek to the owning child (not the parent).

### 2.8 Acceptance criteria & measurement

Add a load-test harness (none exists — no gatling/jmeter/k6/wrk/vegeta/Locust found); recommend k6 for built-in `p(95)`/`p(99)`. Measure end-to-end throughput (`POST /simulations/run-async` → row `COMPLETED`) at 1 replica vs N, recording p95/p99 and consumer lag; expect near-linear scaling until the partition count is the ceiling, with p99 bounded by the lease. Verify a rolling restart (`kubectl rollout restart`) under load drops nothing: no row left `PENDING` with no claim, no DLT growth, every accepted request reaching a terminal state. Commit the numbers with the exact harness command to `docs/PHASE_4_MEASUREMENTS.md`.

### 2.8.1 Measured constraint — payload write cost (from the PR-D0 baseline)

Measured on the 1-replica baseline (shared dev laptop, `docs/PHASE_4_MEASUREMENTS.md`):
every `COMPLETED` simulation persists the full Monte Carlo result — raw JSON ≈
**4.27 MB**, stored in Postgres as compressed jsonb ≈ **1.47 MB** per row (≈
**7.89 MB** bytes-per-row incl. TOAST + indexes), and ≈ **3.90 MB** as the serialised
JSON status response body — at the default shape (1000 paths × 90 days). The
completion `UPDATE`'s Postgres execute time varied **0.13 s
to 3.2 s** with host load, and `updatedAt` is stamped *before* that `UPDATE` runs
(`SimulationRepository.complete()`), so `server_latency_ms` (`updatedAt − createdAt`,
~30–80 ms) structurally *understates* the end-to-end wait by exactly this amount.

**Unexplained gap — stored payload vs bytes-per-row.** The per-row table share
(≈ 7.89 MB on a fresh 20-row table) is about **5×** the stored payload (≈ 1.47 MB)
even with no bloat. Possible causes, **not investigated in PR-D0**: dead tuples or
extra TOAST writes left by the PROCESSING→COMPLETED `UPDATE`, or the payload being
written more than once. This is unexplained and remains an open item for PR-D1 until
measured.

**Consequence:** the single-replica ceiling looks bound by **Postgres WAL/disk I/O on
the payload write, not worker CPU** (no lock waits were logged; autovacuum and
checkpoint contention were not ruled out) — the worker's own Monte Carlo completed in
~30–80 ms in the same runs. **Cheapest next check for PR-D1:** capture `pg_stat_wal`
and checkpoint counters (`pg_stat_bgwriter`, `pg_stat_checkpointer`) around a run, or
repeat Scenario B with the payload column excluded from the write, before relying on
this attribution. **Specifically, compare `pg_stat_wal.wal_bytes` before and after
one simulation against the ≈ 1.47 MB stored payload:** if WAL written per simulation
is several times the payload, the issue is *how the row is written*, not the
hardware. This **must be re-checked before relying on the CPU-based
HPA (Q4)**: if payload persistence is the real constraint, CPU utilisation may
saturate late or not at all, and the HPA signal would need to change (or the write
cost to drop).

**Open question — Q9 (payload storage):** store a summary and percentiles in
`result_payload` instead of all paths, with the full trace offloaded (object store or a
separate table). That directly attacks the write cost this baseline is bound by. Recorded
as open for the design owner; does not alter decisions Q1–Q8.
**Write-path data for Q9 (no decision here):** `docs/PHASE_4_MEASUREMENTS.md` →
*Write-path investigation - writes per simulation and WAL under local Postgres config
(2026-10-15)* — exactly one statement writes the payload per simulation, and the local WAL
knobs cut under-load WAL by 14–34 % without improving throughput.
**Payload-write diagnostic for Q9 (no decision here, throwaway build):**
`docs/PHASE_4_MEASUREMENTS.md` → *Payload-write diagnostic - a no-payload worker build
(throwaway, 2026-10-15)* — a local-only worker build that skips the completion payload write
shows ≈ 10x throughput at 1 replica and a flat 1-vs-3-replica result; Q9 stays open.
**Response-size control for Q9 (no decision here, throwaway build):**
`docs/PHASE_4_MEASUREMENTS.md` → *Write cost vs response cost (2026-10-16)* — a second
local-only build keeps the full payload write and shrinks only the status body (P2 ≈ P0), so
the no-payload speedup is the write, not the response, and at 3 replicas P1's own limit is
api CPU (Kafka lag 0); Q9 stays open.
**Q9 decision note (no decision here, docs-only):** `docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md`
— the payload-consumer survey (which endpoints/DTOs read `allPaths`/`percentileSeries`/
`distribution`), the per-field sizes (`allPaths` = 99.22 % of the payload), the current
status/response flow, and four storage options with their consumer impact, migration needs,
cost hypothesis and P0/P1/P2 verification; it names which option the measurements favour but
**decides nothing** — Q9 stays open.

### 2.9 PR breakdown and per-PR file list (each independently shippable and reversible)

**PR-D0 — prerequisites (FIRST).** *(merged)* Partitions → 6; `max.poll.interval.ms` → 10m; `claim-lease` → 12m; replace `nack()` with per-partition pause/seek/resume; add `ErrorHandlingDeserializer`; add the k6 harness and 1-replica baseline.
- Modify: `src/main/resources/application.yaml` (partitions, poll interval, lease, deserializer), `src/main/java/.../common/config/KafkaConsumerConfig.java` (concurrency, container registry wiring), `src/main/java/.../simulation/event/SimulationRequestedEventConsumer.java` (per-partition pause on unclaimable), `src/main/java/.../simulation/config/SimulationProperties.java` (lease default).
- Add: `loadtest/run-async.js`, `loadtest/README.md`, `docs/PHASE_4_MEASUREMENTS.md` (baseline).

**PR-D1 — shared kernel + multi-module restructure (api + shared, behavior unchanged).** *(merged)*
- Add: root aggregator `pom.xml` (`<modules>`), `probity-shared/pom.xml`.
- Move into `probity-shared`: `common/domain/event/Simulation*.java`, `simulation/domain/**` (Simulation + status), `simulation/persistence/**`.
- Move the current module's sources under `probity-api/` (all remaining `src/main` + `src/test`).
- **Tests stay green and unmodified.** Every test file is moved with `git mv` only — no edits to test bodies. The *only* permitted edits are path-forced mechanical ones: the `package`/`import` lines for the types relocated into `probity-shared`. Any such edit will be listed in the PR description. No assertions, fixtures, or `@Test` methods change.

**PR-D2 — worker module.** *(merged)*
- Add: `probity-worker/pom.xml`, `probity-worker/src/main/java/.../WorkerApplication.java`, worker `application.yaml`, `Dockerfile.worker`.
- Move: async test slice to `probity-worker/src/test/java/...` (`git mv`; see PR-D1 note on path-forced edits).
- Modify: `compose.yaml` (add `simulation-worker`).
- Add: `probity-api/src/main/resources/db/migration/V<next>__worker_role_grants.sql` — **guarded and idempotent**: it only grants privileges, skips the grants when the role is absent, and never contains the role password (the role is created out of band) (§2.2, §2.4).
- **Ordering dependency:** the worker DB role is created **out of band** and must exist before this grant migration runs; the migration itself does nothing to create it.

**PR-D3 — disable the listener in the monolith.** *(merged, PR #13)*
- Modify: `SimulationRequestedEventConsumer.java` (add `@ConditionalOnProperty`), `KafkaConsumerConfig.java` (gate if chosen), `application.yaml` (flag default).
- Add: monolith "no longer consumes" integration test.
- **Note (2026-10-13, split baseline):** the PR-D2 build still consumes in the api — the api → worker dependency puts the listener on the api classpath, so the consumer group had **two members**. This makes the gate a **correctness** fix, not just hygiene; the new test must boot the api slice with the flag `false` and assert, at runtime, that the api forms **no** consumer membership and holds **no** partitions (while the worker holds all 6). See `docs/PHASE_4_MEASUREMENTS.md` → *Split baseline*.

**PR-D4 — Kubernetes.** *(merged, PR #14)*
- Add: `k8s/simulation-worker-deployment.yaml`, `k8s/simulation-worker-service.yaml`, `k8s/simulation-worker-hpa.yaml` (`maxReplicas = partitions ÷ concurrency-per-pod`), `k8s/README.md`.

**PR-D5 — docs + measurements.** *(this PR, #16 — docs-only)*
- Modify: `docs/PHASE_4_MEASUREMENTS.md` (N-replica numbers, p95/p99, rolling-restart result), `docs/plans/PHASE_4_WORKER_EXTRACTION.md` (status).

Extraction must not start before **PR-D0** lands: without >1 partition and the per-partition fix, replicas cannot scale past one, and the load-test harness is what proves it.

### 2.10 Risks & open questions

Recorded decisions (resolved — see **Decisions**): Q1, Q2, Q3, Q4, Q6. Still open: **Q5, Q7, Q8**.

1. **Q1 — payload enrichment:** RESOLVED — Option B (read-only coupling) now; payload enrichment is a deferred fast-follow.
2. **Q2 — partition ceiling:** RESOLVED — 6.
3. **Q3 — image strategy:** RESOLVED — separate worker image.
4. **Q4 — HPA scope:** RESOLVED — CPU-based HPA now; KEDA later.
5. **Q5 — shared-kernel churn (PR-D1):** OPEN. The extraction is a **path move only** — tests are `git mv`-ed, not rewritten; the only edits are package/import lines forced by types moving into `probity-shared` (listed in the PR). Approving PR-D1 is therefore **not** approval to modify test behavior or to break the "tests stay green and unmodified" constraint.
6. **Q6 — lease vs poll interval:** RESOLVED — invariant confirmed: `lease 12m > max.poll.interval.ms 10m ≥ slowest run`.
7. **Q7 — worker DB role:** OPEN (single mechanism proposed). The role (SELECT+UPDATE on `simulations`, SELECT on the four tables + `flyway_schema_history`) is **created out of band by ops/DBA, with its password held in a secret — never in the migration**. The api-owned `V<next>__worker_role_grants.sql` **only grants privileges, skipping the grants when the role is absent**, and is idempotent. Confirm this mechanism and that ops will create the role before the grant migration.
8. **Q8 — Redis:** OPEN. Confirmed not needed by the worker execution path; flag if a future `@Cacheable` is added to the portfolio read.
9. **Q9 — payload storage:** OPEN. The PR-D0 baseline indicates the completion write (an ≈1.47 MB stored / ≈7.89 MB bytes-per-row `result_payload` per row) is the single-replica constraint, not worker CPU (§2.8.1); decide whether to store a summary and percentiles instead of all paths before finalising the HPA signal (Q4). The status endpoint returns the whole ≈ 3.90 MB result on completion; a separate result endpoint, or a summary in the status response, is an API change for the design owner.

---

*Stop for review. No implementation code will be written until this note is approved.*
