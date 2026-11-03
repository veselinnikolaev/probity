# M2 — Reconciliation Sweeper

**Technical design and implementation plan**

| | |
|---|---|
| Status | Design only. No production code, migration, commit or push in this change. |
| Baseline | `master` @ `973df3f` (merge of `9786f10`, the M1 outbox commit) |
| Date | 2026-11-03 |
| Scope | Detect stranded async simulation workflows and re-drive them through the **existing** outbox publisher and Kafka consumer. No second publisher, no direct Kafka send, no simulation execution in the sweeper. |
| Related | `RELIABILITY_ASSESSMENT.md` §5 M1/M2, `docs/decisions/0012-transactional-outbox.md`, `M1_ACCEPTANCE_SUMMARY.md` |

---

## 0. Executive summary

M1 added a transactional outbox. If M1 worked as documented, the largest hole in the async simulation path — "the row committed, the Kafka send was lost, the row is stranded forever" — would already be closed. M2 would then only need to cover the **residual** cases that the outbox cannot cover:

1. the outbox says *published*, but no Kafka delivery will ever reach the consumer (topic retention, offset reset/loss, a consumer group that skipped the record);
2. a `PROCESSING` row whose message will never be redelivered (offset already committed, message lost after the claim);
3. rows that are inconsistent at the schema level (a `PENDING` row with no outbox row, or an outbox row with no simulation);
4. no visibility into any of the above.

That is still worth building, but it is **defense in depth, not the top priority**. The top priority is that M1 at `HEAD` does not build, its 9/9 acceptance claim cannot be true, and the API still contains a second, fire-and-forget Kafka publisher that M1 was supposed to replace. **M2 must not start until M1 is actually green.** Section 1 records the evidence. Section 12 gives the conditional verdict.

The smallest safe M2 is:

> A config-gated, worker-side scheduled sweeper that (a) resets a *published* outbox row back to unpublished for a `PENDING` simulation that has been idle past a grace period, and (b) atomically resets a stale `PROCESSING` simulation to `PENDING` and its outbox row to unpublished. It never sends to Kafka, never executes a simulation, never inserts or deletes an outbox row, and never invents a terminal state. Missing-outbox and orphan-outbox cases are **alert-only**.

---

## 1. Preconditions: M1 is not green at HEAD

This section is not a style complaint. Every item below blocks a correct M2 for a concrete reason.

### 1.1 The worker module does not compile

`SimulationApplicationService` implements `SimulationPort`, but the two signatures disagree:

- `probity-worker/src/main/java/me/veselin/probity/simulation/port/SimulationPort.java:22` declares
  `runSimulationAsync(..., String idempotencyKey)` — **8 parameters**.
- `probity-worker/src/main/java/me/veselin/probity/simulation/service/SimulationApplicationService.java:78` defines
  `runSimulationAsync(..., String idempotencyKey, Map<String, BigDecimal> marketDataSnapshot)` — **9 parameters**.
- The class is `@Service` and not abstract (`SimulationApplicationService.java:33`), so javac reports
  `SimulationApplicationService is not abstract and does not override abstract method runSimulationAsync(...) in SimulationPort`.

Reproduction (already run during this review):

```powershell
.\mvnw.cmd -pl probity-worker -am "-Dtest=OutboxIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `probity-worker` **BUILD FAILURE**, compile error, **zero tests executed**. Any claim that the M1 suite passed at this commit is false for this reason alone.

### 1.2 The M1 integration fixtures never create the row they assert on

`BaseAsyncSimulationIntegrationTest.seedPendingSimulation()` (lines 265–281) only saves a `Simulation`. It never inserts into `simulation_outbox`. `OutboxIntegrationTest` calls that fixture in `@BeforeEach`, then asserts an outbox row exists in tests 1, 3, 4, 5, 6, 8 and 9. Those tests cannot pass as written. Only test 2 inserts an outbox row, and it does so manually to prove rollback.

### 1.3 Test cleanup leaks outbox rows

`BaseAsyncSimulationIntegrationTest.cleanWorkspace()` (lines 224–232) deletes simulations, positions, portfolios and users, but **not** `simulation_outbox`. V20 deliberately has no foreign key to `simulations`, so outbox rows survive between tests. That breaks any assertion on `lockPendingOutboxBatch(1)` and makes test order matter.

### 1.4 The API still has a second Kafka publisher

This is the most important architectural defect for M2.

`probity-api/src/main/java/me/veselin/probity/bff/controller/SimulationController.java`:

- line 135 captures `marketDataSnapshot`;
- line 137 calls `simulationPort.runSimulationAsync(...)` with **8 arguments**, not passing the snapshot;
- line 151 then calls `publishSimulationRequestedEvent(...)`;
- lines 335–353 build a `SimulationRequestedEvent` and call `domainEventPublisher.publish(event)`;
- `probity-api/src/main/java/me/veselin/probity/simulation/event/SimulationEventPublisher.java` turns that into `kafkaTemplate.send(topic, key, event)` with only `whenComplete` logging — the original fire-and-forget path.

So at `HEAD` the request path has **both**:

1. the outbox row (written by the service), which the worker's `OutboxPublisher` will publish; and
2. a direct, best-effort Kafka send from the API controller.

The direct send is at-least-once safe (the consumer is idempotent), but it:

- violates the single-publisher invariant that the M2 task explicitly requires;
- masks outbox failures in normal traffic, so M1 can look healthy while the durable path is untested;
- will keep compiling against the 8-arg interface; a "fix" that adds an 8-arg overload/default method instead of changing the controller would preserve the double publish;
- makes the snapshot inconsistent between the two copies: the service's outbox event receives whatever the controller passes as the 9th argument, while the controller's direct event has the real snapshot.

After M1 remediation, the controller must **not** publish. The API accept path is: capture snapshot → service writes `PENDING` + outbox in one transaction → return 202. The only Kafka producer for `SimulationRequestedEvent` is `OutboxPublisher`.

### 1.5 What must be true before M2 starts

| # | Precondition | Evidence required |
|---|---|---|
| P1 | `SimulationPort` and `SimulationApplicationService` signatures agree; `probity-worker` compiles | `.\mvnw.cmd -pl probity-worker -am -DskipTests compile` exits 0 |
| P2 | Controller passes the snapshot to the service and does **not** publish directly | No `domainEventPublisher.publish` in `SimulationController`; one producer for `SimulationRequestedEvent` |
| P3 | `seedPendingSimulation()` inserts both the simulation and its outbox row | M1 tests 1/3/4/5/6/8/9 reach their assertions |
| P4 | `cleanWorkspace()` deletes `simulation_outbox` before `simulations` | repeated/random test order is stable |
| P5 | The M1 suite actually runs and passes | `OutboxIntegrationTest`: 9/9, 0 failures, 0 errors, **non-zero tests executed** |
| P6 | Worker starts against a migrated DB with the worker role | `/actuator/health/readiness` UP; `has_table_privilege` verified |
| P7 | `M1_ACCEPTANCE_SUMMARY.md` is corrected | Remove the false "9/9 executed" claim or replace it with the real run |

Until P1–P5 hold, an M2 "pass" is meaningless: the M2 tests would sit on the same broken harness, and the M1 defects would be indistinguishable from M2 defects.

---

## 2. Current architecture and state transitions

### 2.1 Components and responsibilities

| Component | Module | Responsibility | Must never do |
|---|---|---|---|
| `SimulationController.runAsync` | api | Authenticate, capture request-time snapshot, call the port, return 202 | Publish to Kafka (after M1 remediation) |
| `SimulationApplicationService.runSimulationAsync` | worker (shared on api classpath) | In one transaction: insert `PENDING` simulation + `SimulationRequestedEvent` outbox row | Send to Kafka; execute the simulation |
| `OutboxPublisher` | worker | Poll unpublished, unleased outbox rows; claim with `claim_token` + `claimed_until`; send to Kafka; fenced `markOutboxPublished` / `recordOutboxFailure` | Execute the simulation; mutate `simulations`; drop a row |
| `KafkaTemplate` (`simulationKafkaTemplate`) | worker | Durable producer used only by `OutboxPublisher` | Be called from a controller (after remediation) |
| `SimulationRequestedEventConsumer` | worker | Claim row (`claimForProcessing`), execute, fenced complete/release, ack/park | Publish to Kafka; reset a row it does not own |
| `SimulationApplicationService.executeAsync` / `MonteCarloSimulationService` | worker | Pure execution under a fence token; write result with `completeIfClaimHeld` | Claim or release; decide delivery ownership |
| `SimulationFailedRecordRecoverer` | worker | On exhausted retries/poison payload: `markFailedIfPending` then DLT publish | Mark a `PROCESSING` row failed |
| `ReconciliationSweeper` (M2) | worker | Detect DB-visible stranded states; conditionally reset outbox/simulation so the existing publisher/consumer re-drive | Send to Kafka; execute; insert/delete outbox rows; write `FAILED` |

### 2.2 Data model

`simulations` (V9, V16, V17) — relevant columns:

| Column | Notes |
|---|---|
| `id` | UUID, application-generated |
| `status` | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` (`SimulationStatus`) |
| `created_at` | set on insert; may be the only timestamp on a `PENDING` row |
| `updated_at` | nullable; **also the consumer's fence token**, truncated to microseconds |
| `deleted` / `deleted_at` | soft delete. Sweeper must ignore `deleted = true` |
| `version` | optimistic lock, **not** bumped by bulk JPQL/native updates |
| request fields | persisted, but **`marketDataSnapshot` is not** — it exists only in the outbox payload |

`simulation_outbox` (V20):

| Column | Notes |
|---|---|
| `id` | PK; the service sets it equal to `simulation_id`, but do not rely on that — join on `simulation_id` |
| `simulation_id` | `UNIQUE`; **no FK** to `simulations` |
| `payload` | JSONB `SimulationRequestedEvent`, including the snapshot |
| `published_at` | non-null means the producer got a broker ack |
| `error_count` | consecutive publication failures; alerting only; never drops the row |
| `claim_token` | publisher fence |
| `claimed_until` | publisher lease/backoff deadline |

Indexes today: `(user_id, status)`-style user scans, `(portfolio_id, created_at DESC)`, and `simulation_outbox(created_at) WHERE published_at IS NULL`. There is **no** index for a global `(status, state_age)` scan. This is an M2 schema requirement, not an optimization.

### 2.3 State transition table

`state_since = COALESCE(updated_at, created_at)`. A `PENDING` row created by the service may have `updated_at = NULL`, so every staleness predicate must use the `COALESCE`.

| # | From | Trigger | Guard / fence | To | Owner | Notes |
|---|---|---|---|---|---|---|
| 1 | — | `POST /simulations/run-async` | one DB transaction | `PENDING` + outbox row | `SimulationApplicationService` | Outbox row is `published_at NULL` |
| 2 | `PENDING` | Kafka delivery | `claimForProcessing`: `status='PENDING'` (or stale `PROCESSING`) | `PROCESSING`, `updated_at = now()` | consumer | `now()` is the fence token |
| 3 | `PROCESSING` (stale) | Kafka redelivery | `status='PROCESSING' AND updated_at < now() - lease` | `PROCESSING`, new `updated_at` | consumer | Reclaim; old worker's writes are fenced out |
| 4 | `PROCESSING` | successful execution | `status='PROCESSING' AND updated_at = fenceToken` | `COMPLETED` + result + paths | worker | 0 rows = claim lost; no overwrite |
| 5 | `PROCESSING` | execution failure | `status='PROCESSING' AND updated_at = fenceToken` | `PENDING`, new `updated_at` | consumer | Release **before** rethrow |
| 6 | `PENDING` | retries exhausted / poison | `status='PENDING'` (not fenced) | `FAILED` | DLT recoverer | Terminal; DLT publish ordering gap (F5) |
| 7 | `PROCESSING` | M2 sweep | `status='PROCESSING' AND state_since = observed AND state_since < now() - processingGrace` | `PENDING`, `updated_at = now()` | sweeper | Only if an outbox row exists |
| 8 | `PENDING` | M2 sweep | outbox `published_at IS NOT NULL AND (claimed_until IS NULL OR claimed_until < now()) AND last_reconciled_at outside cooldown` | `PENDING` (unchanged) + outbox `published_at = NULL` | sweeper | Re-drive; never touches the simulation row |
| 9 | any | soft delete | API ownership guard | `deleted = true` | API | Consumer acks; sweeper skips |
| 10 | `COMPLETED` / `FAILED` | anything | terminal | unchanged | — | Duplicate Kafka deliveries are acked; sweeper never mutates |

Outbox transitions:

| From | Trigger | To | Notes |
|---|---|---|---|
| (none) | accept transaction | unpublished, unclaimed | `published_at NULL`, `claim_token NULL` |
| unpublished, claimable | publisher claim | unpublished, claimed | `claim_token`, `claimed_until = now + lease` |
| unpublished, claimed | broker ack + fenced mark | published | `error_count = 0`, claim cleared |
| unpublished, claimed | send failure + fenced record | unpublished, backoff | `error_count + 1`, `claimed_until = retryAt` |
| unpublished, claimed | publisher crash | unpublished, claimable after lease | No DB write; lease expiry is the recovery |
| published | M2 sweep | unpublished, unclaimed | `reconcile_count + 1`, `last_reconciled_at = now()` |
| published | never | published | Publisher never re-scans published rows; terminal simulations are drained once |

### 2.4 Happy path (after M1 remediation)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant API as API (Controller + Service)
    participant DB as PostgreSQL
    participant P as OutboxPublisher (worker)
    participant K as Kafka
    participant W as Consumer (worker)

    C->>API: POST /simulations/run-async
    API->>DB: BEGIN; INSERT simulations(PENDING); INSERT simulation_outbox; COMMIT
    API-->>C: 202 Accepted + Location
    P->>DB: SELECT ... FOR UPDATE SKIP LOCKED; UPDATE claim_token/claimed_until; COMMIT
    P->>K: send(topic, simulationId, event).get(30s)
    K-->>P: ack
    P->>DB: UPDATE published_at=now() WHERE claim_token=token; COMMIT
    K->>W: deliver
    W->>DB: claimForProcessing -> PROCESSING, fence=now()
    W->>W: execute simulation
    W->>DB: completeIfClaimHeld(fence) -> COMPLETED; COMMIT
    W-->>K: ack
```

### 2.5 Crash windows

| Window | What is durable | What is missing | Recovered by |
|---|---|---|---|
| C1: after accept commit, before publisher claim | sim + outbox | Kafka message | `OutboxPublisher` (M1) |
| C2: after publisher claim commit, before send | sim + outbox + lease | Kafka message | lease expiry + `OutboxPublisher` (M1) |
| C3: after broker ack, before `published_at` | sim + outbox unpublished | published marker | duplicate publish after lease (M1, at-least-once) |
| **C4: after `published_at`, before consumer delivery** | sim `PENDING` + outbox published | Kafka delivery (lost/retained/offset-skipped) | **M2 only** |
| **C5: after consumer claim, before/around execute, no redelivery** | sim `PROCESSING` | execution + delivery | Kafka redelivery if it exists; **M2 only** if it does not |
| C6: after execute, before complete | sim `PROCESSING` | result | redelivery if it exists; **M2 only** if it does not |
| C7: after complete, before ack | sim `COMPLETED` | ack | duplicate delivery acked by consumer (M1) |
| C8: sweeper transaction before commit | unchanged | — | next sweep |
| C9: sweeper after commit | outbox reset and/or sim `PENDING` | — | publisher/consumer |

C4 and C5 are the entire reason M2 exists.

---

## 3. Failure scenarios and recovery strategies

### 3.1 Classification vocabulary

| Class | Meaning | M2 action |
|---|---|---|
| **A — auto-recover** | The sweeper can make a safe, idempotent state change that lets the existing publisher/consumer finish the work | Reset outbox and/or simulation |
| **R — existing retry** | M1 already retries; M2 must not duplicate it | Observe/alert only |
| **M — manual** | Safe reconstruction is impossible or requires data the system does not have | Alert + runbook |
| **O — observe only** | Nothing is broken yet, or the state is terminal | Metric/alert only |

### 3.2 Scenario matrix

| # | Scenario | Observable state | Class | Strategy | Why not more |
|---|---|---|---|---|---|
| S1 | `PENDING`, outbox unpublished | `published_at NULL`, `claimed_until NULL/expired` | **R** | `OutboxPublisher` claims and sends forever; `error_count` drives the alert | Re-publishing here would duplicate M1 |
| S2 | Outbox unpublished, `error_count` high, broker down | `error_count >= 5`, `claimed_until` in future | **R/O** | M1 backoff to 5 min, never drops; WARN at 5; M2 alerts on age/error | A second retry loop, or marking it dead, would silently discard work |
| S3 | Publisher claim expired (publisher crashed mid-send) | `claimed_until < now()`, `published_at NULL` | **R** | M1 re-claims; at-least-once duplicate is safe | No M2 action |
| S4 | `PENDING`, outbox published, no consumer delivery | `published_at NOT NULL`, `state_since` old, no live consumer claim | **A** | Reset outbox to unpublished; publisher re-publishes; consumer's claim is idempotent | This is C4 |
| S5 | `PENDING`, outbox published, publisher currently sending | `claimed_until > now()` | **O** | Skip; the publisher is live | Resetting would race a live send |
| S6 | `PROCESSING` beyond lease, no redelivery | `status='PROCESSING'`, `state_since` old, outbox row exists | **A** | One transaction: reset outbox (if published) + conditional `PROCESSING → PENDING`; publisher redrives | This is C5. Resetting only the simulation would leave no message |
| S7 | `PROCESSING` fresh / legitimate long run | `state_since` within `processingGrace` | **O** | No action | Preempting a live worker wastes compute and is a false positive |
| S8 | `PROCESSING` stale, **no outbox row** | `LEFT JOIN` finds nothing | **M** | Alert; do not reset the simulation | Resetting would create a `PENDING` row with no durable event — a worse permanent strand. The snapshot is not on `simulations`, so a correct event cannot be reconstructed |
| S9 | `PENDING`, **no outbox row** (legacy/pre-M1 or a partial write) | no outbox join | **M** | Alert; operator decides: re-accept with the original request (new id) or mark `FAILED` with a user-visible error | No snapshot, no INSERT grant, no safe reconstruction |
| S10 | Outbox row with no simulation (orphan) | `LEFT JOIN simulations` finds nothing | **M/O** | Alert; publisher will publish, consumer will ack "missing" and skip | Worker has no `DELETE`; deleting is not needed and would lose audit |
| S11 | Terminal simulation with unpublished outbox | `COMPLETED`/`FAILED`, `published_at NULL` | **R** | Publisher publishes; consumer acks terminal | Self-resolving; do not add a new "superseded" mechanism for this |
| S12 | Duplicate Kafka delivery | two records, same key | **O** | Consumer's `claimForProcessing`/terminal ack makes it exactly-once in effect | No sweeper action |
| S13 | Two sweepers pick the same row | same candidate in two cycles | **O** | `FOR UPDATE SKIP LOCKED` + conditional updates + cooldown; one wins | No leader election required |
| S14 | Sweeper crashes mid-action | transaction open | **O** | Rollback; next cycle re-detects | One transaction per row |
| S15 | Scheduler thread stops / sweeper hangs | no last-success update | **M/O** | Watchdog metric + alert; separate scheduler pool; DB statement timeout; pod restart | A sweeper cannot heal itself if its thread is dead; the alert is the recovery |
| S16 | Sweeper keeps resetting the same row | `reconcile_count` rising | **M/O** | Exponential cooldown; alert at 3 and 10; operator inspects Kafka lag/consumer crashes | Never auto-`FAILED`: that would hide a possibly transient infrastructure failure |

### 3.3 What M1 already handles (do not rebuild)

- Atomic `PENDING` + outbox commit (once P1/P2 are fixed).
- Durable, unbounded retry of unpublished outbox rows with exponential backoff.
- At-least-once publish, including crash-after-ack duplicates.
- Publisher claim expiry and fenced success/failure writes.
- Consumer idempotency: atomic claim, fenced complete/release, terminal duplicate ack.
- Crash-before-ack redelivery.
- Stale `PROCESSING` reclaim **when a Kafka delivery exists**.
- Partition parking for unclaimable/fresh rows.
- DLT for poison payloads and exhausted retries (with the F5 ordering caveat, which is M3).

### 3.4 What only M2 can handle

- C4: the outbox says published but the delivery will never arrive.
- C5: a `PROCESSING` row exists but no delivery will ever be redelivered.
- S8/S9/S10: schema-level inconsistency; detection and alerting.
- S15: scheduler liveness.
- Aggregate visibility: stuck counts, oldest age, reconcile loops.

---

## 4. Reconciliation algorithm

### 4.1 Principles

1. **The sweeper is a state-flipper, not a worker.** It never calls Kafka and never runs a simulation. Its only success is making the existing M1 mechanisms able to do their job.
2. **Never create work you cannot describe.** No outbox `INSERT`. If the event is missing, the request snapshot is gone; do not fabricate it.
3. **Never write a terminal state.** `FAILED` remains the DLT/manual path. The sweeper may retry forever; that is intentional and observable, not silent.
4. **Every action is conditional and fenced.** A stale observation must not overwrite a newer transition.
5. **Detection is cheap; action is rarer.** Scan with a bounded, indexed query; act on a bounded number per cycle with a per-row cooldown.
6. **One transaction per row.** No Kafka I/O or long computation inside a transaction. Lock only the outbox row for microseconds.

### 4.2 Detection queries

All thresholds use **database `now()`**, not JVM `Instant.now()`, so clock skew between worker pods does not change what is considered stale. `state_since = COALESCE(s.updated_at, s.created_at)`.

**Q1 — `PENDING` with a published, unclaimed outbox row**

```sql
SELECT s.id,
       COALESCE(s.updated_at, s.created_at) AS state_since,
       o.id                                AS outbox_id,
       o.published_at,
       o.claimed_until
  FROM simulations s
  JOIN simulation_outbox o ON o.simulation_id = s.id
 WHERE s.deleted = FALSE
   AND s.status = 'PENDING'
   AND COALESCE(s.updated_at, s.created_at) < now() - make_interval(secs => :pendingGraceSeconds)
   AND o.published_at IS NOT NULL
   AND (o.claimed_until IS NULL OR o.claimed_until < now())
   AND (o.last_reconciled_at IS NULL
        OR o.last_reconciled_at < now() - make_interval(secs => :cooldownSeconds))
 ORDER BY state_since ASC
 LIMIT :batchSize;
```

**Q2 — stale `PROCESSING` with an outbox row**

```sql
SELECT s.id,
       COALESCE(s.updated_at, s.created_at) AS state_since,
       o.id                                AS outbox_id,
       o.published_at,
       o.claimed_until
  FROM simulations s
  JOIN simulation_outbox o ON o.simulation_id = s.id
 WHERE s.deleted = FALSE
   AND s.status = 'PROCESSING'
   AND COALESCE(s.updated_at, s.created_at) < now() - make_interval(secs => :processingGraceSeconds)
   AND (o.last_reconciled_at IS NULL
        OR o.last_reconciled_at < now() - make_interval(secs => :cooldownSeconds))
 ORDER BY state_since ASC
 LIMIT :batchSize;
```

The `JOIN` (not `LEFT JOIN`) is deliberate: a stale `PROCESSING` row with no outbox is S8, which must be alert-only. Do not silently convert it to `PENDING`.

**Q3 — `PENDING` with no outbox (alert-only)**

```sql
SELECT s.id, COALESCE(s.updated_at, s.created_at) AS state_since
  FROM simulations s
  LEFT JOIN simulation_outbox o ON o.simulation_id = s.id
 WHERE s.deleted = FALSE
   AND s.status = 'PENDING'
   AND o.id IS NULL
   AND COALESCE(s.updated_at, s.created_at) < now() - make_interval(secs => :pendingGraceSeconds)
 LIMIT :batchSize;
```

**Q4 — orphan/unpublished outbox (alert-only)**

```sql
SELECT o.id, o.simulation_id, o.created_at
  FROM simulation_outbox o
  LEFT JOIN simulations s ON s.id = o.simulation_id
 WHERE s.id IS NULL
   AND o.published_at IS NULL
   AND (o.claimed_until IS NULL OR o.claimed_until < now() - make_interval(secs => :orphanGraceSeconds))
 LIMIT :batchSize;
```

**Q5 — reconcile loop (alert-only)**

```sql
SELECT o.id, o.simulation_id, o.reconcile_count
  FROM simulation_outbox o
 WHERE o.reconcile_count >= :alertThreshold
   AND o.last_reconciled_at > now() - make_interval(secs => :recentWindowSeconds)
 LIMIT :batchSize;
```

Q3/Q4/Q5 only feed gauges, counters and WARN logs. They never mutate.

### 4.3 Decision table

| Detection | Simulation action | Outbox action | Commit? | Outcome class |
|---|---|---|---|---|
| Q1, outbox unclaimed | none | `published_at = NULL`, clear claim, `reconcile_count + 1`, `last_reconciled_at = now()` | only if 1 row | auto-recovered |
| Q1, outbox claimed (`claimed_until > now()`) | none | none | — | observe |
| Q1, outbox already reset by a peer | none | none (0 rows) | — | observe |
| Q2, outbox published | `PROCESSING → PENDING` fenced on `state_since` | same as Q1 | one transaction, both writes | auto-recovered |
| Q2, outbox unpublished | `PROCESSING → PENDING` fenced on `state_since` | leave unpublished (publisher will send) | one transaction | auto-recovered |
| Q2, simulation changed since scan | none | rollback the transaction | — | observe |
| Q2, outbox missing | none | none | — | alert (S8) |
| Q3/Q4/Q5 | none | none | — | alert/manual |
| any terminal/deleted row | none | none | — | observe |

### 4.4 Algorithm

```
sweepOnce():
    startedAt = clock.now()
    candidates = []

    candidates += queryPendingNoDelivery(pendingGrace, cooldown, batchSize)   // Q1
    candidates += queryStaleProcessing(processingGrace, cooldown, batchSize)  // Q2

    actions = 0
    for candidate in candidates ordered by state_since:
        if actions >= maxActionsPerCycle: break
        try:
            if candidate is Q1:
                if resetOutboxForPending(candidate): actions++
            else:
                if resetProcessing(candidate): actions++
        catch (LockNotAvailable | OptimisticLock | DataAccessException e):
            log.warn("reconcile.skip", id, reason)
        catch (Exception e):
            log.error("reconcile.error", id, e)
            // never rethrow out of the scheduled method

    alertOnlyScan(Q3, Q4, Q5)
    publishMetrics(candidates, actions, startedAt)
    lastSuccessfulCycleAt = clock.now()
```

`resetOutboxForPending(candidate)`:

```
tx = REQUIRES_NEW
  row = SELECT id, published_at, claimed_until, last_reconciled_at
          FROM simulation_outbox
         WHERE simulation_id = :simId
         FOR UPDATE SKIP LOCKED
  if row is null: return false
  if row.claimed_until is not null and row.claimed_until >= now(): return false
  if row.last_reconciled_at is not null and row.last_reconciled_at >= now() - cooldown: return false
  if row.published_at is null: return false
  UPDATE simulation_outbox
     SET published_at = NULL,
         claim_token = NULL,
         claimed_until = NULL,
         reconcile_count = reconcile_count + 1,
         last_reconciled_at = now()
   WHERE id = row.id
     AND published_at IS NOT NULL
     AND (claimed_until IS NULL OR claimed_until < now())
  return updated == 1
commit
```

`resetProcessing(candidate)`:

```
tx = REQUIRES_NEW
  outbox = SELECT ... FOR UPDATE SKIP LOCKED WHERE simulation_id = :simId
  if outbox is null: return false            // S8: never strand a PENDING row

  if outbox.published_at is not null:
      if outbox.claimed_until >= now(): return false
      if outbox.last_reconciled_at within cooldown: return false
      UPDATE simulation_outbox SET published_at = NULL, claim_token = NULL,
             claimed_until = NULL, reconcile_count = reconcile_count + 1,
             last_reconciled_at = now()
       WHERE id = outbox.id AND published_at IS NOT NULL
         AND (claimed_until IS NULL OR claimed_until < now())

  updated = UPDATE simulations
               SET status = 'PENDING', updated_at = now()
             WHERE id = :simId
               AND deleted = FALSE
               AND status = 'PROCESSING'
               AND COALESCE(updated_at, created_at) = :observedStateSince
  if updated == 0:
      rollback()          // a peer completed/claimed it; undo any outbox reset
      return false
  return true
commit
```

The `state_since = :observedStateSince` predicate is the fence for the simulation write. A worker that completes between the scan and the action changes `updated_at`, so the sweeper matches 0 rows and rolls back. That is the same idea as `completeIfClaimHeld`, using the observed state time instead of a token the sweeper never had.

### 4.5 Multi-instance behavior

- Both worker replicas may scan and choose the same row. The outbox `SELECT ... FOR UPDATE SKIP LOCKED` serializes the action; the loser sees 0 rows or skips.
- The conditional `UPDATE simulations ... AND state_since = observed` makes the simulation reset idempotent even if both somehow reach it; the second matches 0.
- `last_reconciled_at` + cooldown prevents a second sweep from immediately undoing/redoing the first.
- No Redis lock, no ShedLock, no leader election. ADR-0008 already says no Redis in the worker; adding it for this would be a new dependency and a new failure mode.
- `pg_try_advisory_lock` is an optional optimization to reduce duplicate scans, **not** a correctness requirement. Do not ship it in the smallest scope.

### 4.6 Explicit non-goals

The sweeper does **not**:

- send to Kafka, create a `KafkaTemplate`, or reuse the producer;
- execute a simulation or call `SimulationPort.executeAsync`;
- insert, delete, or rewrite outbox payloads;
- write `FAILED`;
- touch `COMPLETED`, `FAILED`, or soft-deleted rows;
- repair a missing outbox row;
- change `error_count` (that is the publisher's counter);
- delete outbox history;
- add a new table, a new topic, a new consumer group, or a new worker deployment.

### 4.7 Sequence — recovery of a lost Kafka delivery (C4)

```mermaid
sequenceDiagram
    autonumber
    participant DB as PostgreSQL
    participant S as ReconciliationSweeper
    participant P as OutboxPublisher
    participant K as Kafka
    participant W as Consumer

    Note over DB: sim PENDING since T0; outbox published_at=T1; no delivery (lost/retained)
    S->>DB: Q1 detects state_since < now()-pendingGrace, unclaimed
    S->>DB: BEGIN; SELECT outbox FOR UPDATE SKIP LOCKED
    S->>DB: UPDATE outbox published_at=NULL, reconcile_count+1; COMMIT
    P->>DB: claim row (claim_token, claimed_until)
    P->>K: send(event)
    K-->>P: ack
    P->>DB: mark published (fenced)
    K->>W: deliver
    W->>DB: claimForProcessing -> PROCESSING
    W->>W: execute
    W->>DB: completeIfClaimHeld -> COMPLETED
    W-->>K: ack
```

If the original message was merely delayed and arrives too, the consumer acknowledges it as a duplicate (S12). That is the at-least-once contract, not a bug.

### 4.8 Sequence — recovery of abandoned `PROCESSING` (C5)

```mermaid
sequenceDiagram
    autonumber
    participant DB as PostgreSQL
    participant S as ReconciliationSweeper
    participant P as OutboxPublisher
    participant K as Kafka
    participant W as Consumer

    Note over DB: consumer claimed at T0, then died; offset committed/lost, no redelivery
    S->>DB: Q2 detects PROCESSING, state_since < now()-processingGrace
    S->>DB: BEGIN; SELECT outbox FOR UPDATE SKIP LOCKED
    alt outbox published
        S->>DB: UPDATE outbox published_at=NULL, reconcile_count+1
    end
    S->>DB: UPDATE simulations SET status=PENDING, updated_at=now()
    Note over S,DB: WHERE status=PROCESSING AND state_since=observed
    S->>DB: COMMIT
    P->>DB: claim outbox
    P->>K: send(event)
    K->>W: deliver
    W->>DB: claimForProcessing (PENDING) -> PROCESSING
    W->>W: execute
    W->>DB: completeIfClaimHeld -> COMPLETED
```

If the original worker is actually alive but slow, its next fenced write matches 0 rows (`updated_at` changed), it stops without side effects, and the redrive wins. Wasteful, never incorrect. This is why `processingGrace` must be conservative.

---

## 5. Database changes

### 5.1 V21 — additive only

`probity-api/src/main/resources/db/migration/V21__simulation_reconciliation.sql` (proposed; **not created by this design task**):

```sql
-- =============================================================================
-- V21__simulation_reconciliation.sql
-- Additive support for the M2 reconciliation sweeper. No data rewrite.
-- =============================================================================

-- Global staleness scan: "all live rows of status X whose state is older than T".
-- The existing (user_id, status) index cannot serve a cross-user sweep.
CREATE INDEX IF NOT EXISTS idx_simulations_status_state_age
    ON simulations (status, COALESCE(updated_at, created_at))
    WHERE deleted = FALSE;

-- Reconciliation bookkeeping on the outbox row (one row per simulation).
ALTER TABLE simulation_outbox
    ADD COLUMN IF NOT EXISTS reconcile_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_reconciled_at TIMESTAMPTZ;

COMMENT ON COLUMN simulation_outbox.reconcile_count
    IS 'Number of times the sweeper reset this row to unpublished. Alerting/backoff only; never a delete threshold.';
COMMENT ON COLUMN simulation_outbox.last_reconciled_at
    IS 'Last sweeper reset. Drives the per-row cooldown so two sweepers cannot loop on one row.';

-- Grants: the sweeper only SELECTs and UPDATEs. It never INSERTs or DELETEs.
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'probity_worker') THEN
        GRANT SELECT, UPDATE ON simulation_outbox TO probity_worker;
        GRANT SELECT, UPDATE ON simulations       TO probity_worker;
    END IF;
END $$;
```

### 5.2 Why each piece

| Change | Why it is necessary | Why the alternative is worse |
|---|---|---|
| `(status, COALESCE(updated_at, created_at)) WHERE deleted = FALSE` | Q1/Q2/Q3 must not seq-scan `simulations` every minute; the partial predicate keeps dead rows out | Reusing `(user_id, status)` would require an N-user loop and cannot bound a global sweep |
| `reconcile_count` | Detect a loop (S16) without a second table; drive backoff | Counting logs is not queryable; a new table adds a lifecycle |
| `last_reconciled_at` | Per-row cooldown, multi-sweeper safety, audit | A fixed global interval still lets two sweepers reset the same row twice |
| No FK | Matches V19/V20; `TRUNCATE simulations` in the test harness must keep working | Adding an FK now would break the existing test cleanup and force a delete order |
| No new grants | The sweeper does not insert/delete; least privilege is already sufficient | `INSERT` would be needed only for auto-repair, which is explicitly out of scope (no snapshot, S9) |

### 5.3 Index creation note

`CREATE INDEX` (non-concurrent) takes a `SHARE` lock that blocks writes for the duration of the build. For the current data volume this is expected to be sub-second. If production `simulations` is large, the migration can be split into an operational step using `CREATE INDEX CONCURRENTLY` outside Flyway's transaction; that is an ops decision, not a design requirement. Do **not** put `CONCURRENTLY` in a normal transactional migration.

### 5.4 Rollout and rollback

- Deploy order: V21 (API owns Flyway; worker validates). The worker's entity does not need the new columns until the sweeper is enabled.
- Enable with `probity.reconciliation.enabled=true` in the worker only.
- Rollback: set the flag false (immediate, no data loss), then optionally drop the index/columns. The columns are inert if unused.
- No backfill. Existing rows start at `reconcile_count = 0`, `last_reconciled_at = NULL`.

### 5.5 Optional extensions (do not ship in the smallest scope)

- `simulations.reconciled_count` / `last_reconciled_at` for audit when the outbox row is missing. Adds a second place to reason about and does not enable auto-repair.
- `simulation_outbox.superseded_at` to stop publishing events for terminal simulations. Not needed: the publisher drains them and the consumer acks them (S11). Adding it changes the M1 publisher predicate and creates a new "dropped without delivery" concept.
- A separate `reconciliation_audit` table. The structured logs and `last_reconciled_at` are enough for the first version.

---

## 6. Transaction boundaries and concurrency

### 6.1 Boundaries

| Operation | Transaction | Locks | I/O inside? |
|---|---|---|---|
| Q1/Q2/Q3/Q4/Q5 scans | read-only / autocommit | none | no |
| `resetOutboxForPending` | `REQUIRES_NEW` | one outbox row, `FOR UPDATE SKIP LOCKED` | no |
| `resetProcessing` | `REQUIRES_NEW` | one outbox row (if present), then one simulation row | no |
| `OutboxPublisher` claim | `REQUIRES_NEW` | `FOR UPDATE SKIP LOCKED` batch | no (commits before send) |
| `OutboxPublisher` send | none | none | Kafka |
| `OutboxPublisher` mark/fail | short transaction | row, fenced on `claim_token` | no |
| Consumer claim/complete/release | per-call transaction | row, fenced on `updated_at` | execution happens between |

The sweeper never holds a lock while calling Kafka or running a simulation. The longest lock is two `UPDATE`s on one row.

### 6.2 Fencing and conditional writes

- **Simulation reset:** `WHERE status='PROCESSING' AND COALESCE(updated_at, created_at) = :observedStateSince`. Any transition since the scan (complete, release, another reclaim) changes `updated_at` and defeats the reset.
- **Outbox reset:** `WHERE published_at IS NOT NULL AND (claimed_until IS NULL OR claimed_until < now())`, plus the row lock and the cooldown. A live publisher send has `claimed_until > now()` and is skipped.
- **No token is invented.** The sweeper is not a consumer; it does not need the consumer's fence. It uses the observed state as its optimistic predicate and rolls back if the world moved.
- **`version` is not bumped.** Bulk updates bypass JPA, exactly like M1's `claimForProcessing` / `completeIfClaimHeld`. The conditional predicate is the concurrency control; adding an entity-level optimistic lock here would be inconsistent and would not help.

### 6.3 Crash windows in the sweeper itself

| Where it crashes | State | Next cycle |
|---|---|---|
| before `BEGIN` | unchanged | re-detect |
| after outbox `UPDATE`, before simulation `UPDATE` | transaction open → rollback | re-detect; outbox still published |
| after simulation `UPDATE`, before `COMMIT` | transaction open → rollback | re-detect |
| after `COMMIT` | outbox unpublished and/or simulation `PENDING` | publisher/consumer take over; sweeper sees a fresh `state_since` |
| during `rollback()` on a 0-row simulation update | transaction rolls back | re-detect if still stale |
| process killed mid-transaction | PostgreSQL aborts the connection's transaction | re-detect |

There is no window where the simulation is `PENDING` and the outbox is guaranteed unpublished and unclaimed **and** the row was supposed to be reset: the two writes are in one transaction.

### 6.4 Lock ordering and deadlocks

- Sweeper: `simulation_outbox` → `simulations`.
- Publisher: `simulation_outbox` only.
- Consumer: `simulations` only (and `simulation_paths` on complete).
- DLT recoverer: `simulations` only.

No cycle exists. The sweeper's outbox lock uses `SKIP LOCKED`, so it never waits behind the publisher's claim. If a future change makes the consumer touch the outbox, re-check this order.

### 6.5 Clock policy

- All sweeper thresholds use database `now()`.
- The consumer's claim still uses JVM `Instant.now()` (M1). Residual F9 clock skew remains; the sweeper must tolerate it, not "fix" it by changing M1 in this milestone.
- If the JVM clock is behind the DB, the sweeper may see a publisher lease as expired while the publisher is still sending; the result is a duplicate publish, which is safe. If the JVM clock is ahead, the consumer may reclaim a fresh row; that is pre-existing F9 and is bounded by the lease.
- Recommendation outside M2: require NTP and monitor clock offset. A later change can move M1's claim predicates to DB `now()`.

### 6.6 Statement and lock timeouts

Set a short `statement_timeout` (e.g., 5s) and `lock_timeout` (e.g., 2s) on the sweeper's transactions via `SET LOCAL`. A hung scan must not pin a connection or block the scheduler. Spring's `@Transactional` does not expose a query timeout portably; use a `TransactionTemplate` with a `JdbcTemplate` `SET LOCAL` at the start of the action transaction, or a dedicated `JdbcTemplate` with `queryTimeout`. Keep it simple: a `TransactionTemplate` + `SET LOCAL statement_timeout = '5s'` is enough for v1.

---

## 7. Retry and timeout policy

### 7.1 Configuration

All under `probity.reconciliation`, worker-only:

| Property | Default | Rationale |
|---|---|---|
| `enabled` | `false` in api, `true` in worker | Explicit opt-in; API has no `@EnableScheduling` anyway |
| `sweep-interval-ms` | `60000` | One minute is cheap; the grace periods are much larger |
| `initial-delay-ms` | `60000` | Do not sweep during startup before the DB pool is warm |
| `pending-grace` | `15m` | A normal publish + consume is seconds; 15m is far outside normal jitter |
| `processing-grace` | `30m` | Must exceed `claim-lease` (12m) + `max.poll.interval.ms` (10m) + skew/run margin. **Measure p99 and raise it if a legitimate run can exceed 30m** |
| `reconcile-cooldown` | `30m` | Per-row; prevents two sweepers/cycles from looping |
| `scan-batch-size` | `50` | Bounded index scan |
| `max-actions-per-cycle` | `25` | Blast-radius cap; a mass event cannot rewrite the world in one minute |
| `orphan-grace` | `1h` | Orphan outbox is rare; avoid noisy alerts during a deploy |
| `alert-after-reconciles` | `3` | First loop signal |
| `severe-after-reconciles` | `10` | Page/manual |
| `max-cooldown` | `6h` | Backoff cap; a row is still retried forever |
| `statement-timeout-ms` | `5000` | A hung query must not stall the scheduler |
| `lock-timeout-ms` | `2000` | Skip a contended row rather than wait |

Cooldown is `min(maxCooldown, baseCooldown * 2^reconcile_count)` for the outbox row. This is **backoff, not a retry limit**: the row is never abandoned.

### 7.2 Escalation ladder

| Attempts (`reconcile_count`) | Behavior | Signal |
|---|---|---|
| 1–2 | Silent INFO action; normal backoff | `actions_total{outcome="reset"}` |
| 3–9 | WARN per action; alert `ReconcileLoop` | `alerts_total{reason="repeat"}` |
| 10+ | WARN + page; backoff up to 6h; still retries | `severe` alert; runbook |
| Missing outbox / orphan | No mutation; WARN once per row per window | `alerts_total{reason="missing_outbox"|"orphan_outbox"}` |
| Terminal row | No action | `stuck_rows{status="COMPLETED"}` is always 0 by construction |

There is **no** automatic terminal transition in M2. The existing DLT path is the only automatic `FAILED` writer, and an operator is the only other one. This satisfies "bounded retry/backoff/terminal without silently discarding work": the sweep is bounded per cycle, the backoff is bounded, and no work is dropped.

### 7.3 Scheduler resilience

- `@Scheduled(fixedDelayString = "${probity.reconciliation.sweep-interval-ms}")` — fixed **delay**, so a slow cycle does not overlap itself.
- The scheduled method catches every per-row exception and every alert-scan exception. It must never throw; a thrown exception is logged by Spring but is a silent operational failure if it repeats.
- `spring.task.scheduling.pool.size: 2` in the worker. Default is 1. `OutboxPublisher` can hold the single scheduler thread for up to `publish-batch-size * send-timeout` (10 × 30s = 5 min) during a broker outage. Without a second thread, the sweeper is starved exactly when it is most needed.
- Optional (recommended) dedicated `ThreadPoolTaskScheduler` bean named `reconciliationScheduler` with one thread, so the sweeper and publisher never share a pool. This is an operational config change, not a new component.
- A `lastSuccessfulCycleAt` gauge and an alert if the age exceeds `3 * sweep-interval`. This is the watchdog for a dead/stuck scheduler thread.
- If the pod dies, the other replica sweeps. If all pods die, no processing happens; on restart the sweep resumes. No permanent stop.
- Do not use a Kubernetes CronJob for v1: it would need its own DB credentials, fencing and observability, and would duplicate the worker's lifecycle.

---

## 8. Metrics, logs and alerting

### 8.1 Metrics (Micrometer)

Cache the scan results in atomics; do not query the DB on every Prometheus scrape.

| Metric | Type | Labels | Meaning |
|---|---|---|---|
| `probity_simulation_reconciliation_candidates` | gauge | `state` (`pending_no_delivery`, `stale_processing`, `pending_no_outbox`, `orphan_outbox`) | Last scan count |
| `probity_simulation_reconciliation_actions_total` | counter | `state`, `action` (`reset_outbox`, `reset_simulation`), `outcome` (`applied`, `skipped`, `failed`) | Work done |
| `probity_simulation_reconciliation_duration_seconds` | timer | — | Cycle duration |
| `probity_simulation_reconciliation_last_success_timestamp_seconds` | gauge | — | Watchdog |
| `probity_simulation_reconciliation_alerts_total` | counter | `reason` (`missing_outbox`, `orphan_outbox`, `repeat`, `query_failed`) | Alert-only findings |
| `probity_simulation_stuck_rows` | gauge | `status` (`PENDING`, `PROCESSING`) | Rows older than their grace |
| `probity_simulation_outbox_unpublished` | gauge | — | `COUNT(*) WHERE published_at IS NULL` |
| `probity_simulation_outbox_oldest_unpublished_age_seconds` | gauge | — | Age of the oldest unpublished row |
| `probity_simulation_outbox_max_error_count` | gauge | — | Worst `error_count` |
| `probity_simulation_outbox_oldest_published_age_seconds` | gauge | — | How long a published row has been waiting (a proxy for C4) |
| `probity_simulation_reconcile_count_max` | gauge | — | Worst `reconcile_count` |

The last two are the key new signals: M1 exposes no "published but not consumed" view today.

### 8.2 Structured logs

One line per action, at INFO:

```json
{
  "event": "simulation.reconcile",
  "simulationId": "...",
  "fromStatus": "PROCESSING",
  "toStatus": "PENDING",
  "outboxId": "...",
  "action": "reset_simulation+reset_outbox",
  "stateAgeSeconds": 2140,
  "reconcileCount": 2,
  "reason": "stale_processing",
  "outcome": "applied"
}
```

Rules:

- Never log `payload`, `marketDataSnapshot`, user holdings, or the full event.
- Log IDs only; the outbox payload may contain portfolio-derived market data.
- WARN for skipped/failed/repeat; ERROR only for an invariant violation (e.g., a 0-row simulation update after the outbox was reset — which should roll back, so it is not an error).
- Include `traceId` if tracing is enabled. Note: the worker currently has no tracing bridge (`OBSERVABILITY_AUDIT_REPORT.md`), so do not claim spans.

### 8.3 Alerts

| Alert | Expression (sketch) | For | Action |
|---|---|---|---|
| `ReconciliationSweeperStalled` | `time() - probity_simulation_reconciliation_last_success_timestamp_seconds > 300` | 5m | Check scheduler/pod; restart if needed |
| `PendingStuck` | `probity_simulation_stuck_rows{status="PENDING"} > 0` | 15m | Check Kafka/consumer; inspect ids |
| `ProcessingStuck` | `probity_simulation_stuck_rows{status="PROCESSING"} > 0` | 30m | Check worker crashes/lag; do not manually reset a live worker |
| `OutboxNotDraining` | `probity_simulation_outbox_oldest_unpublished_age_seconds > 600` | 10m | Broker/publisher; check `error_count` |
| `OutboxRepeatedFailure` | `probity_simulation_outbox_max_error_count >= 5` | 10m | Broker/auth/topic; the row is still queued |
| `ReconcileLoop` | `increase(probity_simulation_reconciliation_actions_total{outcome="applied"}[1h]) > 20` | 5m | A root cause is persisting; pause sweeper if it amplifies |
| `MissingOutbox` | `increase(probity_simulation_reconciliation_alerts_total{reason="missing_outbox"}[1h]) > 0` | 5m | Manual runbook |
| `OrphanOutbox` | same with `orphan_outbox` | 1h | Manual inspection |

Add the rules to the existing `monitoring/prometheus-rules.yaml` only when the metrics exist; a rule for a non-existent metric is noise.

### 8.4 Health

A custom `ReconciliationHealthIndicator` exposing:

- `lastSuccessfulCycleAt`, `lastCycleDurationMs`, `candidates`, `actions`, `stalled`.

Return `UP` normally, `UNKNOWN`/`OUT_OF_SERVICE` when stalled. **Do not include it in the `readiness` group.** A stalled sweeper must not remove a healthy Kafka consumer from service; that would turn a partial degradation into a total outage. Keep it as a separate health group/alert.

---

## 9. Security and operational considerations

### 9.1 Least privilege

The worker already has `SELECT, UPDATE` on `simulations` (V18) and `SELECT, UPDATE` on `simulation_outbox` (V20). The sweeper needs nothing else. Specifically:

- **No `INSERT`** on `simulation_outbox`. This is why S8/S9 are manual; it is also a safety boundary: the sweeper cannot fabricate an event without a payload it does not have.
- **No `DELETE`** anywhere.
- **No new DB role.** No Kafka admin client, no topic creation, no consumer-group mutation.
- The API keeps `SELECT, INSERT` on the outbox and remains the only writer of new events.
- Flyway remains API-only; V21 is added there.

### 9.2 Blast radius

- `max-actions-per-cycle` and `scan-batch-size` cap how many rows a bug can touch per minute.
- The cooldown prevents a single row from being reset on every cycle.
- The sweeper only flips two flags; it cannot corrupt results, because completion remains fenced by the consumer.
- A false positive costs duplicate compute, not duplicate results: the first successful fenced completion wins, and later writes match 0 rows.
- Disabling `probity.reconciliation.enabled` stops all sweeper behavior immediately; no data migration is needed.

### 9.3 Operational runbook

**`missing_outbox` (S9).** Confirm `simulations.status='PENDING'`, `deleted=false`, and no outbox row. The original snapshot is not stored on the simulation. Options: (a) ask the client to resubmit (new id; the old row can be soft-deleted or marked `FAILED` with a user-visible error); (b) if the request fields are sufficient and stale-price safety is not required, an approved operator script may insert an outbox row with an empty snapshot — this degrades the guarantee and must be an explicit decision. Do not add `INSERT` to the worker role for this.

**`orphan_outbox` (S10).** Check whether the simulation was hard-deleted or never committed. If the row is truly orphaned, leave it; the publisher will publish and the consumer will ack "missing". If it is unpublished and repeatedly failing, inspect the payload. Do not delete; there is no `DELETE` grant and no need.

**`repeat` / `reconcile_count` high (S16).** Check Kafka consumer lag, worker restarts, broker health, and topic retention. If the sweeper is amplifying a lag spike, set `enabled=false` temporarily; the existing publisher/consumer continue. Do not manually `FAILED` a row that is merely delayed.

**`PROCESSING` high age.** Check whether a worker is alive and slow. If so, raise `processing-grace` and investigate the run time; do not reset. If no worker owns it (offset committed, no redelivery), the sweeper's reset is the correct action.

**Scheduler stalled.** Check the worker logs for `Unexpected error occurred in scheduled task`, thread dumps for a blocked Kafka send, and the scheduling pool size. Restart the pod if the thread is wedged; the other replica continues.

### 9.4 Kubernetes

- The existing `k8s/simulation-worker-deployment.yaml` has 2 replicas. Both may sweep; the algorithm is safe. No StatefulSet, no leader election.
- Add `spring.task.scheduling.pool.size: 2` (or a dedicated scheduler bean) to the worker config.
- No new ports, secrets, or RBAC. The sweeper uses the existing DB secret.
- Resource impact: one indexed query per minute + at most 25 two-statement transactions. Negligible compared with simulation compute, but the index must exist before enabling.

### 9.5 PII and audit

- Logs contain simulation/outbox IDs, statuses, ages, counts. No payload, no snapshot, no user holdings.
- `last_reconciled_at` and `reconcile_count` are the durable audit trail for automatic actions.
- If a future compliance requirement needs per-action audit, add a table; do not put payloads in logs.

---

## 10. Integration-test strategy

### 10.1 Harness

- Testcontainers PostgreSQL 16 and the existing shared Kafka broker (`AsyncKafkaTestSupport`), which already validates real persistence, real claims and real concurrency.
- Fix the M1 harness first: `seedPendingSimulation()` must insert the outbox row; `cleanWorkspace()` must delete `simulation_outbox` before `simulations`.
- Disable the scheduler in tests (`probity.reconciliation.enabled=false` or `sweep-interval-ms=1h`) and call `sweeper.sweepOnce()` directly. This makes every test deterministic; no sleeps for scheduling.
- Manipulate time with SQL (`now() - interval '40 minutes'`), not `Thread.sleep`.
- Do not annotate test classes with `@Transactional`; it would hide the commits the sweeper depends on. Use `JdbcTemplate` for setup/asserts.
- For end-to-end tests, enable the listener with a per-class group/topic (`AsyncKafkaTestSupport.registerAsyncBroker(registry, true, "m2-recon")`). For DB-only sweep tests, keep it dormant (`false`).
- Count executions with a test `@TestConfiguration` listener for `SimulationCompletedEvent` (an `AtomicInteger`), not by asserting on logs. This proves "exactly once" without modifying production code.

### 10.2 Required tests

| ID | Test | Setup | Assertion |
|---|---|---|---|
| T1 | Stuck `PENDING` recovery | Seed sim + outbox; set `published_at=now()`, `state_since=now()-30m`; run sweeper; run publisher; let the consumer run | Outbox reset exactly once; simulation reaches `COMPLETED`; one completion event |
| T2 | `PENDING` with unpublished outbox is not swept | Seed sim + outbox unpublished, old | Sweeper does nothing; publisher still publishes; `reconcile_count=0` |
| T3 | Live publisher claim is respected | Seed; set `published_at=now()`, `claimed_until=now()+5m`; old state | Sweeper skips; `published_at` unchanged |
| T4 | Stuck `PROCESSING` recovery | Seed; claim to `PROCESSING`; set `updated_at=now()-40m`; outbox published | Sweeper resets both; publisher+consumer complete once; old fence cannot complete |
| T5 | Fresh `PROCESSING` untouched | Claim now; run sweeper | No action; status remains `PROCESSING` |
| T6 | Stale `PROCESSING` with no outbox | Seed sim; set `PROCESSING` old; no outbox row | No mutation; `alerts_total{reason="missing_outbox"}` (or `missing_outbox`) increments; status stays `PROCESSING` |
| T7 | `PENDING` with no outbox | Seed sim only; old | No mutation; alert metric; no fabricated event |
| T8 | Concurrent sweepers | Two threads call `sweepOnce()` with latches on the same candidate | Exactly one applied action; `reconcile_count=1`; one completion |
| T9 | Crash during recovery | In a `TransactionTemplate`, reset the outbox then throw before the simulation update | Both writes roll back; outbox still published; next sweep succeeds |
| T10 | Duplicate Kafka delivery | Produce the same event twice (or publish, then force the outbox reset again) | One completion; second delivery acked as `COMPLETED`; result unchanged |
| T11 | Completed never re-executed | `COMPLETED` row + a produced duplicate + an old unpublished outbox | Sweeper ignores it; consumer acks; completion count stays 1 |
| T12 | Outbox retry exhaustion (M1 interaction) | `error_count=5`, lease expired, broker down | Publisher retries, increments, never drops; sweeper does not clear the error |
| T13 | Soft-deleted rows | `deleted=true`, old `PENDING`/`PROCESSING` | Sweeper ignores; no action; no alert (or a distinct low-priority metric) |
| T14 | Terminal + unpublished outbox | `COMPLETED`/`FAILED`, outbox unpublished, old | Sweeper ignores; publisher drains; consumer acks; no state change |
| T15 | Scheduler watchdog | Stub the repository to throw; call `sweepOnce()` | No exception escapes; `last_success` not advanced; `query_failed` increments; next call succeeds |
| T16 | Index plan | `EXPLAIN` Q1/Q2 on seeded data | Index scan on `idx_simulations_status_state_age`, no seq scan |

### 10.3 What not to test

- Do not SIGKILL a JVM in a unit test to simulate a crash; use a transaction rollback and a lease expiry. The real crash window is covered by the transaction boundary.
- Do not assert on Kafka internal topics or consumer-group offsets; assert on the observable simulation state and completion count.
- Do not test the scheduler's cron arithmetic; test `sweepOnce()`. A separate smoke test can assert the bean is registered and the flag disables it.
- Do not use `Thread.sleep` to age rows; SQL timestamps are deterministic and faster.

### 10.4 Acceptance tests for the whole M2

1. With a published outbox row and no Kafka record, a 20-minute-old `PENDING` simulation reaches `COMPLETED` with no manual SQL, within one sweep + one publish poll.
2. A 40-minute-old `PROCESSING` simulation with a live outbox row is reset and completed exactly once; a fresh `PROCESSING` row is untouched.
3. Two concurrent sweeper instances perform one action per row; `reconcile_count` is exactly 1.
4. A `PENDING`/`PROCESSING` row with no outbox is never mutated and produces an alert metric.
5. A `COMPLETED`/`FAILED`/deleted row is never mutated and never re-executed.
6. The sweeper never calls Kafka and never calls `executeAsync` (verify with a spy/ArchUnit rule or a test bean that fails if invoked).
7. The worker role has no new `INSERT`/`DELETE`; `has_table_privilege` asserts it.
8. The migration is additive and `EXPLAIN` uses the new index.
9. With `enabled=false`, the full M1 behavior is unchanged.

---

## 11. Risks, trade-offs and alternatives

### 11.1 Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| False positive on a slow but live worker | Medium | Wasted compute; one duplicate run | `processing-grace` >> lease; measure p99; fencing makes it correct |
| Consumer lag looks like "lost delivery" | Medium | Duplicate publishes; load amplification | 15m grace, 30m cooldown, batch cap, `ReconcileLoop` alert; optionally pause on lag |
| Scheduler starvation by the publisher | Medium | Sweeper silently stops | `spring.task.scheduling.pool.size: 2` or a dedicated scheduler; watchdog alert |
| Index build locks writes | Low (current size) | Brief write pause | Plain `CREATE INDEX`; use `CONCURRENTLY` out-of-band if the table is large |
| Missing outbox cannot be auto-repaired | Certain | Manual work for legacy rows | Alert-only; documented runbook; no fake snapshot |
| Reconcile loop hides a deeper outage | Medium | Repeated resets | `reconcile_count` backoff + alerts at 3/10; do not auto-`FAILED` |
| Clock skew | Medium | Duplicate or delayed recovery | DB `now()` for sweeps; NTP; do not change M1 in this scope |
| Two producers remain (controller + outbox) | **Certain at HEAD** | Masks M1; violates the design | M1 precondition P2: remove the controller publish |
| M1 is broken, so M2 tests cannot be trusted | **Certain at HEAD** | False confidence | Do not start M2 until P1–P5 |

### 11.2 Trade-offs

- **Time-based detection vs. Kafka lag introspection.** Time is simple, portable and needs no AdminClient; it can produce false positives under lag. Lag-based detection is more precise but couples the sweeper to consumer-group metadata and topic retention. Start with time + conservative grace; add lag later if needed.
- **Reset-to-PENDING vs. mark-FAILED.** Reset preserves at-least-once and user intent; `FAILED` is terminal and hides recoverable work. Reset is correct.
- **One transaction per row vs. batch.** One row per transaction is slower but gives small locks, simple crash semantics and easy fencing. Batches would hold locks and complicate partial failure. The volume is tiny.
- **No leader election vs. advisory lock.** Conditional updates + `SKIP LOCKED` are sufficient and dependency-free. Leader election reduces duplicate scans but adds a new failure mode.
- **No new table vs. audit table.** `reconcile_count`/`last_reconciled_at` plus structured logs cover the first version. A full audit table can come later if compliance requires it.

### 11.3 Alternatives considered

| Alternative | Why rejected |
|---|---|
| Sweeper sends directly to Kafka | A second publisher; duplicates M1's claim/backoff/fencing; two code paths to reason about |
| Sweeper executes the simulation itself | Duplicates the consumer's ownership/offset logic; no fence token; unbounded compute inside a scheduler |
| Auto-insert a missing outbox row with an empty snapshot | Requires `INSERT`; silently weakens the stale-price guarantee; the snapshot is not on `simulations` |
| Mark stale rows `FAILED` | Fabricates a terminal failure for recoverable work; user-visible error; hides infrastructure faults |
| Re-publish on every client status poll | Client-driven, unreliable, amplifies load, and a client that stops polling never recovers |
| Kafka transactions / XA | Rejected in ADR-0012; incompatible with the current consumer ack mode |
| Redis/ShedLock leader election | ADR-0008 says no Redis in the worker; a new dependency and a new outage mode |
| Separate reconciliation microservice/CronJob | New deployment, credentials, observability, and fencing; overkill for two conditional updates |
| A new `reconciliation` table/job queue | More state and more failure modes; the outbox already has the per-simulation identity |
| Reset `PROCESSING` without checking the outbox | Creates a permanent `PENDING` row with no event; strictly worse than the stuck state |

---

## 12. Final assessment

### 12.1 Is M2 genuinely necessary?

**Yes, but narrower than `RELIABILITY_ASSESSMENT.md` implies, and only after M1 is fixed.**

The assessment ranked F6 (no reconciler) first because *any* stranded row had no recovery. M1's outbox removes the main cause of stranded rows (the lost accept-side publish). What remains is real but lower-probability:

- a Kafka message can still be lost after `published_at` (retention, offset reset, a consumer group that never sees it);
- a `PROCESSING` row can still be abandoned with no redelivery;
- schema-level inconsistencies and loops have no visibility;
- a stalled scheduler is invisible.

Those are worth closing. They are **not** the same severity as the pre-M1 F1 hole, and they are not a reason to skip fixing M1.

### 12.2 What M1 already handles vs. what M2 must handle

| Concern | M1 | M2 |
|---|---|---|
| Atomic accept (sim + event) | Yes (once P1/P2 are fixed) | — |
| Unpublished outbox retry/backoff | Yes, forever | Observe/alert only |
| At-least-once publish | Yes | — |
| Duplicate delivery / idempotent execution | Yes | — |
| Crash before ack | Yes (redelivery) | Only if no redelivery |
| Stale `PROCESSING` reclaim | Only when a delivery exists | When no delivery exists |
| Lost Kafka message after publish | **No** | Yes (reset outbox) |
| Missing outbox row | **No** | Alert/manual only |
| Orphan outbox row | **No** | Alert only |
| Stuck/loop metrics and watchdog | **No** | Yes |
| `FAILED` before DLT (F5) | **No** | Out of scope (M3) |
| Idempotency 202/409 lock (F2) | **No** | Out of scope (M4) |
| Graceful shutdown (F10) | **No** | Out of scope (M6) |

### 12.3 Smallest safe scope

Ship exactly this:

1. `ReconciliationSweeper` in the worker, config-gated, `sweepOnce()` public for tests.
2. Q1 (`PENDING` + published outbox → reset outbox) and Q2 (stale `PROCESSING` + outbox → reset outbox + simulation in one transaction).
3. Q3/Q4/Q5 alert-only.
4. V21: the partial `(status, state_since)` index + `reconcile_count` + `last_reconciled_at`; no other schema change.
5. Metrics, logs, watchdog health, and Prometheus rules.
6. Tests T1–T16 (or the subset that covers T1, T4, T6, T8, T9, T10, T11, T15 first).
7. Operational config: `enabled=true` in the worker, `false` in the API; scheduling pool size 2; runbook.

Do **not** ship in v1: auto-insert, auto-`FAILED`, direct Kafka, leader election, a new table, `superseded_at`, Kafka-lag-driven detection, or a separate service.

### 12.4 Acceptance criteria

M2 is accepted when:

1. P1–P5 from §1.5 are satisfied; the M1 suite actually runs and passes.
2. T1/T4/T8/T9 pass against Testcontainers PostgreSQL + Kafka with real commits and two concurrent sweepers.
3. A `PENDING` row with a published outbox and no Kafka record is recovered without manual SQL.
4. A stale `PROCESSING` row is recovered exactly once; a fresh one is untouched.
5. Missing-outbox and orphan-outbox rows are never mutated and produce an alert.
6. `COMPLETED`/`FAILED`/deleted rows are never mutated or re-executed.
7. The sweeper makes no Kafka call and no `executeAsync` call; the worker role still has no `INSERT`/`DELETE` on the outbox.
8. `EXPLAIN` uses the new index; the migration is additive and reversible by config.
9. With the flag off, worker behavior is byte-for-byte the M1 behavior.
10. A stalled scheduler produces an alert within 5 minutes.

### 12.5 Complexity and risk

- **Code:** one scheduled class (~150–200 LOC), three to five repository methods, a small health indicator, config. Low.
- **Schema:** one index + two nullable/defaulted columns. Low.
- **Tests:** 10–16 integration tests, including two-thread concurrency and rollback. Medium (the existing harness must be fixed first).
- **Operational:** a new alert family, a runbook, a scheduler-pool change. Medium-low.
- **Risk:** medium overall, dominated by false positives under consumer lag and by the M1 preconditions. The correctness risk is low because every write is conditional and the consumer's completion remains fenced.

### 12.6 Verdict

- **M2 as described in the original assessment ("a bounded sweep that re-publishes PENDING rows"):** already largely obsolete if M1 works; do not build a second publisher.
- **M2 as scoped here (reset-only, conditional, alert-first):** **conditional GO**, after M1 remediation.
- **M2 before M1 is fixed:** **NO-GO.** At `HEAD` the worker does not compile, the M1 tests cannot run, and the API still has a second producer. Building a sweeper on that base would add a third moving part to a system whose durable hand-off is not demonstrably working.

The single most valuable next action is not the sweeper. It is: fix P1–P5, make the M1 suite genuinely green, remove the controller's direct publish, and verify the worker starts and can read/update its tables. Then the sweeper is a small, safe, well-tested addition.

---

## Appendix A — File references

| File | Relevance |
|---|---|
| `probity-worker/.../simulation/port/SimulationPort.java:22` | 8-arg `runSimulationAsync`; compile mismatch |
| `probity-worker/.../simulation/service/SimulationApplicationService.java:33,78` | Implements the port; 9-arg method; `@Transactional` accept |
| `probity-api/.../bff/controller/SimulationController.java:135,137,151,335-353` | Captures snapshot, calls 8-arg port, then directly publishes to Kafka |
| `probity-api/.../simulation/event/SimulationEventPublisher.java` | Fire-and-forget Kafka producer still in the request path |
| `probity-worker/.../simulation/service/OutboxPublisher.java` | M1 publisher; claim → send → fenced mark; one row per loop iteration |
| `probity-worker/.../simulation/event/SimulationRequestedEventConsumer.java` | Claim/execute/complete/release; parking; terminal ack |
| `probity-shared/.../simulation/persistence/SimulationJpaRepository.java` | All fenced/conditional SQL; outbox lock/claim/mark/fail |
| `probity-shared/.../simulation/persistence/SimulationRepository.java` | Adapter; `findStaleProcessingSimulations` is unused and has no time predicate |
| `probity-api/src/main/resources/db/migration/V20__simulation_outbox.sql` | Outbox schema, partial index, grants; no FK |
| `probity-api/src/main/resources/db/migration/V18__worker_role_grants.sql` | `SELECT, UPDATE` on `simulations` |
| `probity-worker/src/main/resources/application.yaml` | Lease, consumer, outbox config |
| `probity-worker/src/test/resources/application-test.yaml` | Scheduler disabled for tests; Flyway enabled |
| `probity-worker/src/test/java/.../BaseAsyncSimulationIntegrationTest.java:224-232,265-281` | Missing outbox fixture and cleanup |
| `probity-worker/src/test/java/.../OutboxIntegrationTest.java` | 9 claimed tests; invalid fixtures |
| `probity-worker/src/main/java/me/veselin/worker/WorkerApplication.java` | `@EnableScheduling` |
| `docs/decisions/0012-transactional-outbox.md` | M1 decision and rejected alternatives |
| `RELIABILITY_ASSESSMENT.md` §5 | M2 shape and acceptance |
| `M1_ACCEPTANCE_SUMMARY.md:5,168,176,180` | False 9/9 claim; "do not start M2 until tests pass" |

## Appendix B — Verification commands used in this review

```powershell
# Repo state (no production changes made by this design)
git status --short --branch
git log --oneline -3

# The M1 suite at HEAD
.\mvnw.cmd -pl probity-worker -am "-Dtest=OutboxIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
# Expected (and observed): BUILD FAILURE in probity-worker, compile error on
# SimulationApplicationService / SimulationPort, zero tests executed.

# After M1 remediation, the minimum evidence for M2:
.\mvnw.cmd -pl probity-worker -am -DskipTests compile
.\mvnw.cmd -pl probity-worker -am "-Dtest=OutboxIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

No production file, migration, commit or push was created during this design task. The only added file is this document.
