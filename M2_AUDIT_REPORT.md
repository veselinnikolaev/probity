# M2 Reconciliation Sweeper — Audit Report

**Audit date:** 2026-11-04

**Status:** **VERIFIED — implementation, migration, tests, and full regression complete**

**Verified baseline:** branch `master`, HEAD `73031b4` (`fix(reliability): resolve outbox publishing, event isolation, and test suite stability`)

**Scope:** M2 Phase 2 — Reconciliation Sweeper

> **Document control:** This report supersedes the earlier M2 progress audit. The earlier
> findings are preserved verbatim in [§7 Historical findings](#7-historical-findings-superseded)
> and are explicitly labelled as historical. They are retained for audit history and must not
> be read as the current state.

---

## 1. Executive summary

The M2 Reconciliation Sweeper is implemented, compiles cleanly, and is verified against real
PostgreSQL 16 and Kafka 3.7 via Testcontainers.

| Check | Result |
|---|---|
| Clean multi-module compile | **PASS** |
| M1 outbox regression (`OutboxIntegrationTest`) | **PASS — 9/9** |
| M2 reconciliation matrix (`ReconciliationIntegrationTest`, T1–T16) | **PASS — 16/16** |
| Full reactor regression | **PASS — 413 reported, 0 failures, 0 errors, 1 skipped** |
| Architectural invariants | **PASS** |
| Source changes required during validation | **None** |
| M2 commit | Pending at audit time; commit follows this report |

No blockers remain. The single skipped test is the pre-existing
`SimulationAsyncUnclaimableConcurrencyPlaceholderTest` placeholder, not a failure.

---

## 2. Verified build and test evidence

### 2.1 Exact commands and results

| # | Command | Exit | Result |
|---|---|---:|---|
| 1 | `.\mvnw.cmd -pl probity-worker -am compile` | 0 | BUILD SUCCESS (incremental) |
| 2 | `.\mvnw.cmd clean compile -DskipTests` | 0 | BUILD SUCCESS, 4/4 modules |
| 3 | `.\mvnw.cmd -pl probity-worker -Dtest=OutboxIntegrationTest test` | 1 | Test-compile failure caused by a stale installed `probity-shared` artifact (see §2.5) |
| 4 | `.\mvnw.cmd -pl probity-worker -am "-Dtest=OutboxIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | 0 | `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0` |
| 5 | `.\mvnw.cmd -pl probity-worker -am "-Dtest=ReconciliationIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | 0 | `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0` |
| 6 | `.\mvnw.cmd test -pl probity-api -am` | 0 | BUILD SUCCESS, total time 4:55 min |

Clean-compile source counts: `probity-shared` 15, `probity-worker` 74, `probity-api` 112.

> PowerShell note: Maven `-D` arguments containing dots must be quoted, otherwise PowerShell
> parses them as separate lifecycle phases.

### 2.2 Exact Surefire counts by module

| Module | Test files | Tests | Failures | Errors | Skipped | Passed |
|---|---:|---:|---:|---:|---:|---:|
| `probity-shared` | 0 | 0 | 0 | 0 | 0 | 0 |
| `probity-worker` | 15 | 50 | 0 | 0 | 1 | 49 |
| `probity-api` | 33 | 363 | 0 | 0 | 0 | 363 |
| **Total** | **48** | **413** | **0** | **0** | **1** | **412** |

The only skip is `SimulationAsyncUnclaimableConcurrencyPlaceholderTest` (1 test, 1 skipped).

### 2.3 M2 integration matrix (T1–T16)

All 16 scenarios executed and passed against Testcontainers PostgreSQL 16 + Kafka 3.7:

| Test | Scenario | Result |
|---|---|---|
| T1 | `StuckPendingRecovery` | PASS |
| T2 | `PendingWithUnpublishedOutboxNotSwept` | PASS |
| T3 | `LivePublisherClaimRespected` | PASS |
| T4 | `StuckProcessingRecovery` | PASS |
| T5 | `FreshProcessingUntouched` | PASS |
| T6 | `StaleProcessingWithNoOutbox_AlertOnly` | PASS |
| T7 | `PendingWithNoOutbox_AlertOnly` | PASS |
| T8 | `ConcurrentSweepersSafety` | PASS |
| T9 | `CrashDuringRecovery_Rollback` | PASS |
| T10 | `DuplicateKafkaDelivery_Idempotent` | PASS |
| T11 | `CompletedNeverReExecuted` | PASS |
| T12 | `OutboxRetryExhaustion_M1Interaction` | PASS |
| T13 | `SoftDeletedRowsIgnored` | PASS |
| T14 | `TerminalWithUnpublishedOutboxIgnored` | PASS |
| T15 | `SchedulerWatchdog_HealthResilience` | PASS |
| T16 | `SweeperInvariantsAndIndexPresence` | PASS |

### 2.4 M1 regression

`OutboxIntegrationTest` executed all 9 scenarios successfully:

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 2.5 Environment note — the initial `-pl` failure was not a code defect

Without `-am`, Maven resolved the previously installed `probity-shared` JAR instead of the
working tree, producing `cannot find symbol` errors for `lockPendingOutboxBatch`,
`claimOutboxRecords`, `ReconciliationCandidate`, and `findPendingSimulationsWithoutDelivery`.
Re-running with `-am` (the documented fallback) compiled the reactor and passed 9/9. The clean
compile in §2.1 confirms there is no M1 compilation blocker at `73031b4`.

---

## 3. Implementation inventory

### 3.1 Production code

| File | Responsibility |
|---|---|
| `probity-worker/.../reconciliation/ReconciliationSweeper.java` | Scheduled sweeper, Q1/Q2 repairs, Q3–Q5 alert scans, metrics/health updates |
| `probity-worker/.../reconciliation/ReconciliationProperties.java` | All `probity.reconciliation.*` configuration |
| `probity-worker/.../reconciliation/ReconciliationMetrics.java` | Micrometer gauges, counters, and duration timer |
| `probity-worker/.../reconciliation/ReconciliationHealthIndicator.java` | Actuator health detail and stalled detection |
| `probity-shared/.../persistence/SimulationJpaRepository.java` | Detection queries, `FOR UPDATE SKIP LOCKED`, fenced reset updates, aggregate metrics queries |
| `probity-shared/.../persistence/SimulationRepository.java` | Repository facade and row-to-record mapping |

### 3.2 Configuration

- Worker: `probity.reconciliation.*` defaults, scheduler pool size 2.
- API: `probity.reconciliation.enabled: false` (the API must not sweep).
- Tests: reconciliation disabled with a long interval; the sweeper is invoked deterministically.

### 3.3 Migration

`V21__simulation_reconciliation.sql` exists in both required locations:

- `probity-api/src/main/resources/db/migration/V21__simulation_reconciliation.sql` (production)
- `probity-worker/src/test/resources/db/migration/V21__simulation_reconciliation.sql` (test copy)

The two copies are byte-identical. The migration is additive:

- partial index `idx_simulations_status_state_age` on `(status, COALESCE(updated_at, created_at)) WHERE deleted = FALSE`
- `simulation_outbox.reconcile_count INTEGER NOT NULL DEFAULT 0`
- `simulation_outbox.last_reconciled_at TIMESTAMPTZ`
- `SELECT, UPDATE` grants for `probity_worker` when the role exists

The API is the single Flyway owner; the worker sets `spring.flyway.enabled: false` and validates
its entities against the API-maintained schema.

### 3.4 Monitoring and tests

- `monitoring/prometheus-rules.yaml`: sweeper stalled, PENDING/PROCESSING stuck, outbox not
  draining, repeated outbox failure, reconcile loop, missing outbox, orphan outbox.
- `probity-worker/.../async/ReconciliationIntegrationTest.java`: T1–T16.

---

## 4. Architectural invariants — verified

| Invariant | Status | Evidence |
|---|---|---|
| Sweeper never publishes to Kafka | **PASS** | No `KafkaTemplate` field or producer call; T16 asserts this reflectively |
| Sweeper never executes simulations | **PASS** | No `SimulationPort` or `MonteCarloSimulationService` dependency; T16 asserts no execution-engine field |
| Sweeper never creates or deletes rows | **PASS** | Only `SELECT`/`UPDATE`; V21 grants only `SELECT, UPDATE` |
| Sweeper never writes `FAILED` | **PASS** | Q2 resets `PROCESSING → PENDING` only; no `FAILED` write path |
| Recovery is atomic and fenced | **PASS** | `REQUIRES_NEW` transactions; Q2 fenced on observed `state_since`; T9 verifies rollback |
| Concurrent sweepers are safe | **PASS** | `FOR UPDATE SKIP LOCKED`; T8 verifies exactly one action |
| Live publisher claim is respected | **PASS** | `claimed_until` check plus conditional outbox reset; T3 |
| Missing/orphan outbox is alert-only | **PASS** | Q2 missing-outbox branch returns without mutation; Q3/Q4/Q5 alert scans; T6/T7 |
| Terminal rows are ignored | **PASS** | Detection filters `PENDING`/`PROCESSING` and `deleted = FALSE`; T11/T13/T14 |
| M1 fencing and idempotency preserved | **PASS** | Same `updated_at` fence token; T10/T11/T12 |

### 4.1 Concurrency analysis

**Sweeper vs `OutboxPublisher`**

- The publisher claims rows with `FOR UPDATE SKIP LOCKED` and commits `claim_token + claimed_until`
  before Kafka I/O.
- The sweeper locks the same outbox row with `FOR UPDATE SKIP LOCKED`; if the publisher holds the
  lock, the sweeper gets no row. If the claim is committed and `claimed_until > now()`, the sweeper
  skips.
- Q1 targets only `published_at IS NOT NULL` rows; the publisher claims only `published_at IS NULL`
  rows. They do not compete for the same work.
- The sweeper never sends to Kafka; it only makes a row publishable again.

**Sweeper vs `SimulationRequestedEventConsumer`**

- The consumer claims with a fenced `UPDATE` and receives `updated_at` as its fence token.
- Q2's simulation reset is fenced on the observed `state_since`. If the consumer claims or
  reclaims the row between scan and reset, the reset matches 0 rows and the whole transaction
  (including any outbox reset) rolls back.
- Consumer completion and release are also fenced on the same token. A consumer whose lease was
  superseded writes 0 rows and stops without side effects.
- Duplicate deliveries are handled by `claimForProcessing`: fresh `PROCESSING` is parked,
  `COMPLETED`/`FAILED`/missing is acknowledged and skipped. T10/T11 confirm no re-execution.

### 4.2 Residual observations (non-blocking)

1. **Q1 is not fenced on simulation state.** `resetOutboxForPending` verifies the outbox
   claim/cooldown but not that the simulation is still `PENDING`. If a simulation transitions to
   `PROCESSING` or `COMPLETED` between detection and reset, the outbox can still be reset,
   producing an extra duplicate publish. Consumer fencing prevents re-execution (fresh `PROCESSING`
   is parked; `COMPLETED` is skipped), so this is duplicate traffic/alert noise rather than a
   correctness violation. An optional hardening would add the observed `state_since` to the Q1
   reset condition. Not applied because no test demonstrates a defect.
2. **At-least-once re-execution on lease expiry is pre-existing M1/consumer semantics.** M2 does
   not widen it. The worker's `processing-grace` (30m) exceeds `claim-lease` (12m), so a
   legitimately running job is not swept before its own lease can lapse.
3. **No live-cluster deployment test.** Verification is Testcontainers PostgreSQL 16 + Kafka 3.7
   with Flyway V1–V21. That is the intended integration boundary, not a production rollout test.

---

## 5. Operational and production verification requirements

Before deploying the M2 worker:

1. **Apply V21 through the API Flyway owner.** The worker intentionally does not run Flyway. A
   fresh worker must not start against a schema that lacks `reconcile_count` and
   `last_reconciled_at`.
2. **Verify worker grants.** V21 grants `SELECT, UPDATE` on `simulations` and `simulation_outbox`
   only when the `probity_worker` role already exists. If the role is created later, apply the
   grants manually.
3. **Set reconciliation configuration explicitly.** Worker defaults are enabled with 15m/30m
   grace, 30m cooldown, 50 scan batch, 25 actions per cycle, and 6h max cooldown. Override
   `PROBITY_RECONCILIATION_*` for the target environment and confirm the API remains disabled.
4. **Load and verify the Prometheus rules.** Confirm the metric names emitted by
   `ReconciliationMetrics` match `monitoring/prometheus-rules.yaml` in the deployed Prometheus.
5. **Validate in staging with real Kafka/PostgreSQL.** Exercise multi-replica worker concurrency,
   broker outage/restart, and clock synchronization (NTP). The sweeper uses DB `now()` for
   thresholds while the consumer claim uses JVM `Instant.now()`; clock skew remains a residual risk.
6. **Watch the first production cycles.** Monitor `probity_simulation_reconciliation_last_success_timestamp_seconds`,
   stuck-row gauges, outbox age, and reconcile-loop alerts before raising action limits.

---

## 6. Commit scope

The M2 commit contains:

- reconciliation production code (sweeper, properties, metrics, health indicator)
- shared repository queries and fenced reset methods
- V21 migration (API main + worker test copy)
- worker/API/test configuration
- T1–T16 integration test
- Prometheus alert rules
- this audit report

It does **not** include generated build logs, the M1 investigation report, or the unrelated
unstaged M1 documentation edits present in the working tree.

---

## 7. Historical findings (superseded)

The original audit was performed by source inspection only, without test execution, and against
an inconsistent baseline claim. Its findings are retained below **as historical statements** and
are superseded by the verified evidence in §2–§5.

### 7.1 Historical blockers

| ID | Historical claim | Current verified status | Evidence |
|---|---|---|---|
| B1 | M1 compilation blocker: `SimulationApplicationService` did not override `runSimulationAsync` (8 vs 9 params), so `probity-worker` did not compile. | **HISTORICAL — does not exist at `73031b4`.** Both interfaces have the same 9-parameter signature. | Clean compile: `.\mvnw.cmd clean compile -DskipTests` exit 0; 4/4 modules SUCCESS |
| B2 | M1 integration test flaws: missing outbox inserts, cleanup leaks, controller double-publish. | **HISTORICAL — disproven.** | `OutboxIntegrationTest` 9/9 pass; a read-only baseline investigation confirmed the controller has no direct Kafka publish path |
| B3 | V21 migration existed but was not applied; Flyway disabled in both main and test config. | **HISTORICAL — resolved.** V21 is applied by the API Flyway owner; the worker test copy is applied by Testcontainers. Worker Flyway is intentionally disabled because the API is the single owner. | V1–V21 apply successfully in T1–T16; V21 copies are byte-identical |
| B4 | `make_interval(secs => :param)` might fail under Spring/Hibernate native-query parsing. | **HISTORICAL — disproven.** | All Q1–Q5 queries execute in T1–T16 against PostgreSQL 16 |
| B5 | Sweeper enabled by default in production. | **Still true for the worker, by design.** The API explicitly disables it. | `probity-worker/.../application.yaml`; `probity-api/.../application.yaml` |

### 7.2 Historical risks

| ID | Historical risk | Current status |
|---|---|---|
| R1 | Clock skew between JVM and DB. | **Residual operational risk.** Mitigate with NTP and monitor clock offset. |
| R2 | Sweeper/publisher race on `claimed_until`. | **Addressed.** `FOR UPDATE SKIP LOCKED` plus `claimed_until` checks; T3 verifies a live claim is respected. |
| R3 | Soft-deleted rows not excluded. | **Addressed.** Q1/Q2 filter `s.deleted = FALSE`; T13 passes. |
| R4 | Orphan outbox rows with `published_at IS NOT NULL`. | **Accepted by design.** Q4 is alert-only; no reset is correct. |
| R5 | Cooldown too aggressive for slow workers. | **Configurable.** `processing-grace` is 30m vs a 12m claim lease; tune per environment and monitor stuck-row metrics. |

### 7.3 Historical test status table

The original report recorded:

| Test group | Existing tests | Executed | Passed | Failed | Skipped |
|---|---|---:|---:|---:|---:|
| T1–T16 (`ReconciliationIntegrationTest`) | 16 | No (project compile failure) | N/A | N/A | N/A |
| M1 `OutboxIntegrationTest` | 9 | No (compile error) | N/A | N/A | N/A |

That table is **historical only**. The current verified counts are in §2.2–§2.3.

---

## 8. Final verdict

**M2 Phase 2 is verified and ready to commit.** There are no build or test blockers. The remaining
items are operational: apply V21 through the API before starting the worker, verify grants and
monitoring, and validate multi-replica behavior in staging.

**Intended commit message:**

```text
feat(reliability): implement reconciliation sweeper
```
