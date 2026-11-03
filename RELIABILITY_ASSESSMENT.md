# Probity Distributed-System Reliability Assessment

**Date:** 2026-10-31  
**Scope:** API → Kafka → simulation worker → PostgreSQL, plus the Redis dependency used by the
API's idempotency filter.  
**Method:** read the shipped code and configuration first, inventory the protections that already
exist, then enumerate failure modes, rank them by severity × likelihood, and propose testable
milestones.  
**Status:** assessment only. **No production code was changed for this document, and none of the
fault-injection scenarios below was executed** — the worker container is currently not runnable in
this environment (missing out-of-band `WORKER_DB_PASSWORD`), and the brief was explicitly "design
the scenarios, do not implement".

This is the reliability companion to `OBSERVABILITY_AUDIT_REPORT.md`. That report established what
is actually observable; this one asks what is actually safe.

---

## 1. Critical path as shipped

The path is deliberately split: the API owns the **accept** half, the worker owns the **execute**
half, and the row status is the shared contract.

```
POST /simulations/run-async
  │
  1. IdempotencyFilter: SETNX probity:idempotency:{userId}:{key} = PENDING   (Redis)
  │
  2. SimulationApplicationService.runSimulationAsync
  │      INSERT simulations (status=PENDING, no result)
  │
  3. SimulationController.publishSimulationRequestedEvent
  │      kafkaTemplate.send(topic, simulationId, event)   ← fire-and-forget
  │      future.whenComplete(log only)
  │
  4. IdempotencyFilter: SET key = COMPLETED(202, body), TTL 24 h
  │
  └─ 202 Accepted + Location

Kafka simulation-requested (6 partitions, replicas=1)
  │
  worker SimulationRequestedEventConsumer.consume
  5. claimForProcessing: PENDING|stale PROCESSING → PROCESSING, updated_at = fenceToken
  │
  6. MonteCarloSimulationService.runAsync (no ambient transaction)
  │
  7. SimulationRepository.complete: fenced UPDATE + simulation_paths INSERT, one transaction
  │      (0 rows ⇒ claim lost ⇒ SimulationClaimLostException)
  │
  8. ack.acknowledge()   (manual_immediate)
  │
  failure: releaseToPending (fenced) → rethrow → 2 retries → recoverer
             → markFailedIfPending → publish simulation-requested.DLT
```

### 1.1 Invariants the code is built around

| Invariant | Where it is encoded | Current value |
|---|---|---|
| A worker never writes back a row it no longer owns | `completeIfClaimHeld` / `releaseIfClaimHeld` predicate on `updated_at = :fenceToken` | always |
| A live worker is never mistaken for a dead one | `claim-lease > max.poll.interval.ms` | 12 m > 10 m |
| A duplicate delivery never re-runs a finished simulation | consumer `COMPLETED`/`FAILED` → ack + skip | always |
| A failed run is retried, then terminal | `-D` handler: 2 retries, backoff 1 s ×2, then recoverer | 3 attempts |
| A poison payload becomes a DLT record, not an infinite re-poll | `ErrorHandlingDeserializer` + `DeadLetterPublishingRecoverer` | configured |
| Unclaimable work is parked per partition, not per consumer | `UnclaimablePartitionParker` | 1 s backoff |
| One record is processed per poll | `max.poll.records: 1`, listener concurrency unset (=1) | 1 |

The two most important are already covered by tests: `SimulationFencedCompleteIntegrationTest`
(stale fence changes nothing) and `SimulationAsyncRetryBudgetIntegrationTest` (retry → DLT →
FAILED). This is a system with real protections, not a blank sheet.

---

## 2. Existing protections (evidence inventory)

| Protection | Evidence |
|---|---|
| Fenced claim | `SimulationRepository.claimForProcessing` → `SimulationJpaRepository.claimForProcessing` (`WHERE status = 'PENDING' OR (status = 'PROCESSING' AND updatedAt < :staleThreshold)`) |
| Fenced completion with payload + paths in one transaction | `SimulationRepository.complete` `@Transactional`; `completeIfClaimHeld` `AND s.updatedAt = :fenceToken` |
| Fenced release before rethrow | `SimulationRequestedEventConsumer` catch block → `releaseToPending`; `releaseIfClaimHeld` |
| Microsecond fence truncation | `SimulationRepository.now()` → `Instant.now().truncatedTo(ChronoUnit.MICROS)` |
| Terminal-state duplicate suppression | `SimulationRequestedEventConsumer.handleUnclaimable` (`COMPLETED`/`FAILED` → `ack`) |
| Claim-lost mid-run stops without side effects | `SimulationClaimLostException` → ack, no release, no rethrow |
| Per-partition park + seek | `SimulationRequestedEventConsumer.requeue` → `consumer.seek` + `UnclaimablePartitionParker.park` |
| Bounded retry budget | `ExponentialBackOffWithMaxRetries(2)`, initial 1 s, ×2.0 |
| Non-retryable classification (bad input fails fast) | `addNotRetryableExceptions(EmptyPortfolio, InsufficientMarketData, PortfolioNotFound, PositionNotFound, AssetNotFound, SimulationNotFound, AccessDenied, IllegalArgument)` |
| Poison-payload handling | `ErrorHandlingDeserializer` + `spring.json.trusted.packages` |
| DLT publish failure is counted and rethrown (offset not committed) | `SimulationFailedRecordRecoverer` `dltPublishFailures.increment(); throw e;` |
| DLT producer durability knobs | `acks=all`, `retries=3`, `max.block.ms=60000`, `setFailIfSendResultIsError(true)`, 5 s wait |
| `FAILED` only from `PENDING` (never from a live `PROCESSING`) | `markFailedIfPending` `AND s.status = :pendingStatus` |
| Config invariant is tested | `SimulationWorkerConfigInvariantTest` (api module) asserts lease > `max.poll.interval.ms` |
| Idempotency key is scoped per user and atomic | `setIfAbsent` on `{prefix}{userId}:{clientKey}` |
| API never consumes | `probity.simulation.worker.enabled: false` in the API; `SimulationListenerAbsentInApiIntegrationTest` |

---

## 3. Failure-mode register

Severity: **S1** silent data loss or permanently wrong terminal state · **S2** work stuck or
duplicated, manual recovery required · **S3** bounded delay/degradation, self-heals · **S4**
noise/config hygiene.  
Likelihood: **H** expected under normal failure (broker/process/Redis), **M** plausible,
**L** rare.  
Score = severity × likelihood, used only to order the list.

### Ranked summary

Weights: S1=4, S2=3, S3=2, S4=1; H=3, M=2, L=1. The scheme is ordinal — use it to
order the list, not to argue about the difference between an 8 and a 9.

| Rank | ID | Failure mode | Sev | Lik | Score | Protected? |
|---|---|---|---|---|---|---|
| 1 | F6 | No reconciler/sweeper for stranded `PENDING` or abandoned `PROCESSING` rows | S2 | H | 9 | **No** |
| 2 | F1 | PENDING row committed, Kafka publish lost → row stranded forever | S1 | M | 8 | **No** |
| 3 | F5 | DLT publish fails after the row is marked FAILED → next delivery acks, DLT copy never written | S1 | M | 8 | Partial |
| 4 | F2 | Idempotency turns a stranded accept into a permanent one: cached 202, or a 24 h orphaned-PENDING 409 lock | S2 | M | 6 | **No** |
| 5 | F3 | Duplicate PENDING rows across the API boundary (new idempotency key or Redis TTL expiry) | S2 | M | 6 | Partial |
| 6 | F8 | Redis outage fails every POST while readiness still reports UP | S2 | M | 6 | **No** |
| 7 | F7 | Listener concurrency 1 + `max.poll.records: 1` → head-of-line blocking, no lag signal | S3 | H | 6 | Partial |
| 8 | F4 | Crash-after-claim parks the partition for up to the 12 m lease, log-flooding each cycle | S3 | M | 4 | Yes |
| 9 | F10 | No graceful shutdown/drain; in-flight run is interrupted on SIGTERM | S3 | M | 4 | **No** |
| 10 | F9 | Worker clock skew can steal or block a claim | S3 | L | 2 | **No** |
| 11 | F15 | Market-data snapshot failure silently degrades to live prices | S4 | M | 2 | Partial |

F6 ranks first because it is the certain one: *any* stranded row — from F1, a crash, or an
operator mis-click — has no automatic recovery. F1 and F5 are the only S1s and are the
headline defects; they score just below F6 only because their likelihood is bounded by the
send/window analysis in F1.

---

### F1 — Lost Kafka publish after the PENDING commit (critical)

**What happens.** `runSimulationAsync` inserts the row and returns; the controller then publishes
from a *separate* call site, outside that write. `SimulationEventPublisher.publish` calls
`kafkaTemplate.send(...)` and only attaches `whenComplete` logging. The HTTP response does not wait
for the broker.

**Window and failure shapes.** It would be wrong to say "any send failure strands the row". Neither
module sets `acks`, `retries`, `enable.idempotence`, or `delivery.timeout.ms` — the producer block is
serializers only — so the producer runs on Kafka client defaults (`acks=all`, idempotent producer,
`retries=MAX`, `delivery.timeout.ms=120000`, `max.block.ms=60000`). A broker blip shorter than two
minutes, with the API alive, is retried and is **not** lost. The real loss paths are narrower:

| Shape | Surfaces as | Outcome |
|---|---|---|
| API process dies after `INSERT` commits, before the send completes | nothing in any log | row stranded (silent) |
| broker unavailable > `delivery.timeout.ms` while the API stays up | async `TimeoutException` in `whenComplete` | row stranded (silent) |
| `send()` throws synchronously (metadata timeout, buffer exhausted, producer closed) | controller throws → 500 → filter deletes the idempotency key | not silent; the client may retry |
| send succeeds but nothing ever consumes it | consumer lag only | row stranded (silent) |

That is why likelihood is **M**, not **H**: the silent cases need a process death or a
multi-minute outage, not an ordinary broker hiccup. Severity stays **S1** because nothing in the
system recovers the row afterwards — the 202 is already returned, so there is no error path to
compensate on, and `FAILED`/DLT is unreachable because the record never entered Kafka.

**Evidence.** `SimulationController.runAsync` (call at `SimulationController.java:151`),
`SimulationApplicationService.runSimulationAsync` (`:74`), `SimulationEventPublisher.java:36-44`,
producer config at `probity-api/src/main/resources/application.yaml` (`spring.kafka.producer`,
serializers only).

**Consequence.** The user polls `/status` forever. Nothing retries against the row. There is also
no metric for "row stuck in PENDING", so the failure is silent.

**Gap.** No transactional outbox, no await-and-compensate, no reconciler.

**Reproducible scenario (designed).**
1. Start the stack; run one successful `run-async` so the producer has cached topic metadata.
2. `docker compose stop kafka`.
3. Immediately `POST /api/v1/simulations/run-async` with a fresh `Idempotency-Key` — while cached
   metadata still lets the record be buffered rather than throwing.
4. Observe `202 Accepted`; after ~2 minutes the API log shows
   `Failed to publish SimulationRequestedEvent ... TimeoutException`.
5. `SELECT id, status FROM simulations WHERE id = '<id>'` → `PENDING`.
6. `docker compose start kafka`; wait several minutes.
7. Re-query: still `PENDING`; the topic has no record for that id.

The same test also demonstrates the synchronous shape (step 3 after metadata expiry surfaces a
500 and a usable retry) — worth asserting, because it is the one case the system already handles.

**Missing test.** A fault-injected `KafkaTemplate` (send future completes exceptionally) that
asserts either (a) no `PENDING` row survives, or (b) the API returns a non-2xx and the row is
terminal. Today neither property is asserted anywhere. The crash shape needs a process-level test
(kill between commit and send) rather than an HTTP-level one.

---

### F2 — Idempotency turns a stranded accept into a permanent one

Two different Redis states, same filter, same effect: the client cannot get out.

**(a) The cached 202.** `IdempotencyFilter` treats any 2xx as success and rewrites the record as
`COMPLETED(statusCode=202, body)` for 24 h. The `simulationId` in that body is the stranded row. A
retry with the same `Idempotency-Key` is answered from Redis with `X-Cache: Idempotent-Hit`; the
controller — and therefore the publisher — is never invoked again.

**(b) The orphaned PENDING lock.** Before the controller runs, the filter writes
`IdempotencyRecord(PENDING, 0, null)` with the full 24 h TTL. Only a thrown exception inside the
filter's `try` deletes it; a hard process kill (OOM, `SIGKILL`, rolling deploy — including the F1
crash) skips the `catch` and skips the response rewrite. `IdempotencyRecord` carries no timestamp
and no age field, and the filter has no staleness check, so for the remaining 24 h every retry with
that key gets `409 "An identical request is currently processing."` The client is locked out of its
own request and no work is running.

**(c) A new key.** Because the service ignores the key entirely, a retry with a fresh
`Idempotency-Key` creates a second `PENDING` row and a second event (see F3).

**Evidence.** `IdempotencyFilter` (PENDING `setIfAbsent`; the 2xx → `COMPLETED` branch; the
`catch`/`finally`), `IdempotencyRecord.java` (three fields: status, statusCode, responseBody),
`application.yaml` `probity.idempotency.ttl.hours: 24`. `runSimulationAsync` accepts
`idempotencyKey` and never reads it (`SimulationApplicationService.java:57-79`).

**Consequence.** The two standard client recovery moves are both dead ends: retry with the same key
is answered from cache (a) or rejected as a conflict (b); retry with a new key duplicates the work
(c) and never fixes the stranded row.

**Gap.** The cached record means "accepted", but it is used as "done"; and the PENDING lock has no
lease, only a TTL.

**Reproducible scenario (designed).**
- (a) After F1: re-POST the same body and key → `202`, `X-Cache: Idempotent-Hit`, no new row, no new
  event, no change to the stranded row.
- (b) Kill the API container (`docker compose kill app`) mid-request, after the lock is taken but
  before the response is written. `redis-cli GET "probity:idempotency:<userId>:<key>"` still returns
  a PENDING record. Re-POST the same key → `409` for the rest of the TTL.
- (c) Re-POST with a new key → a second `PENDING` row and a second event.

**Missing test.** "A cached 202 whose event was never delivered is re-driven or surfaced as a
failure, not replayed verbatim" and "an orphaned PENDING lock older than a bounded window is
reclaimable instead of blocking for the full TTL."

---

### F6 — No reconciler for stranded rows (systemic)

**What happens.** There is no scheduled job anywhere in the repository (`@Scheduled`,
`EnableScheduling`, `TaskScheduler` → zero matches). `SimulationRepository.findStaleProcessingSimulations`
is unused and, as its own Javadoc says, has **no time predicate** — it returns every `PROCESSING`
row for a user, however fresh. The lease only re-opens a row for *redelivery*; if no delivery is
left (F1) or the record was already acked, nothing looks at the row again.

**Consequence.** Every stranded-row failure in this document has the same terminal answer: manual
SQL. That is the real systemic gap behind F1, F2 and F5.

**Missing test.** "A `PENDING` row older than the reconciliation threshold with no completion is
either re-published idempotently or moved to a terminal state, without operator SQL."

---

### F5 — DLT publish failure loses the dead-letter copy

**What happens.** The recoverer marks the row `FAILED` **before** publishing to the DLT. If the DLT
publish throws, it increments `probity.simulation.dlt.publish.failures` and rethrows so the offset is
not committed. But on the next delivery the listener calls `claimForProcessing`, which fails because
the row is now `FAILED`; `handleUnclaimable` reads `FAILED` and **acknowledges**. The DLT write is
therefore never retried: the row is terminal and the message is skipped.

**Evidence.** `SimulationFailedRecordRecoverer.java:50-72` (DB first, then DLT),
`SimulationRequestedEventConsumer.java:190-193` (`FAILED` → ack), DLT topic 7-day retention
(`application.yaml` `simulation-dlt-retention-ms` default 604800000).

**Consequence.** The user still sees `FAILED` (so this is not a false success), but the promised
dead-letter artifact does not exist. The only copy is the source topic until retention expires, and
there is no replay tooling in the repo.

**Gap.** Ordering: "mark terminal" must not precede "durably dead-lettered", or `FAILED` must not be
a blind ack when the record has not reached the DLT.

**Reproducible scenario (integration-level, deterministic).** Inject a
`DeadLetterPublishingRecoverer`/`KafkaTemplate` that throws on the DLT send, deliver a poison
record, assert: metric increments, offset not committed, and the record is retried to the DLT after
the fault is removed. A manual race variant (`docker compose stop kafka` timed against the retry
window) is possible but timing-sensitive; the injected-template test is the reliable form.

**Missing test.** "A DLT publish failure leaves the record retryable to the DLT, not acknowledged
by the `FAILED` branch."

---

### F3 — Duplicate PENDING rows across the API boundary

**What happens.** Idempotency lives only in Redis. The database has no uniqueness constraint on the
client key, and the service ignores `idempotencyKey`. If the client retries with a new key, or the
idempotency record expires/is evicted, a second `PENDING` row and a second event are created. The
worker deduplicates by `simulationId`, not by request, so both rows are legitimately different work.

**Consequence.** Duplicate compute and duplicate rows for one user intent. Not corrupting, but
expensive and confusing. The worker-side duplicate test only covers the *same* event delivered
twice, which is a different guarantee.

**Missing test.** "Two accepts for the same client intent produce one simulation."

---

### F7 — Single-consumer throughput and no lag signal

**What happens.** Listener concurrency is unset (1) and `max.poll.records: 1`. Six partitions are
assigned to one consumer thread, which processes one simulation at a time. The concurrency > 1 test
is deliberately `@Disabled` until PR-D1. There is no consumer-lag metric (documented in the
observability report) and therefore no lag alert.

**Consequence.** One long run delays everything behind it; a backlog is invisible in the metrics
path. This is a capacity/latency failure mode, not a correctness one.

**Reproducible scenario.** Enqueue >100 requests while one expensive simulation runs; watch
`kafka-consumer-groups.sh --describe --group probity-simulation-worker` grow while Prometheus has no
lag series.

**Missing test.** The disabled `SimulationAsyncUnclaimableConcurrencyPlaceholderTest` becomes the
activation gate for concurrency > 1.

---

### F8 — Redis outage fails all POSTs while readiness stays UP

**What happens.** `IdempotencyFilter` calls `setIfAbsent` unconditionally for every non-excluded
POST, with no try/catch. If Redis is down, the call throws and the request fails. There is no
`management.endpoint.health.group.readiness` configuration, so a Redis outage does not necessarily
remove the pod from readiness — it keeps serving GETs and failing writes.

**Consequence.** Fail-closed writes with healthy-looking instances; load balancers keep routing to
them.

**Reproducible scenario.** `docker compose stop redis` → every POST returns 5xx;
`GET /actuator/health/readiness` still returns 200. Restart Redis → writes recover.

**Missing test.** "Redis unavailable ⇒ writes fail deterministically and readiness reflects it (or
an explicit fail-open policy is chosen and tested)."

---

### F4 — Crash-after-claim blocks the partition until the lease lapses

**What happens.** If the worker dies after `claimForProcessing` but before ack, the row is
`PROCESSING` with a fresh `updated_at`. The redelivery cannot claim it, so it seeks to its own
offset and pauses the partition. This repeats every 1 s until the 12-minute lease expires. That is
bounded and by design, but it is ~720 pause/resume pairs at `INFO` per record, and every record
behind it in that partition waits.

**Consequence.** Up to 12 minutes of per-partition delay, plus log volume. Self-heals.

**Missing test.** Exists in intent (`SimulationAsyncFreshProcessingReclaimIntegrationTest`,
`SimulationAsyncPartitionParkIntegrationTest`), but not the crash-then-restart wall-clock case, and
not the log-volume bound.

---

### F10 — No graceful shutdown

**What happens.** Neither module sets `server.shutdown: graceful`,
`spring.lifecycle.timeout-per-shutdown-phase`, or a listener stop timeout. On SIGTERM the JVM halts
and an in-flight simulation is interrupted.

**Consequence.** Wasted compute; the row is recovered by Kafka redelivery + lease (so correctness
holds), but recovery is slow (F4). Low-to-medium operational cost.

**Missing test.** "SIGTERM during a run leaves a state from which the redelivery completes exactly
once."

---

### F15 — Silent snapshot degradation

**What happens.** `captureMarketDataSnapshot` catches every exception and returns `Map.of()`. The
worker then falls back to live prices and logs a warning (`MonteCarloSimulationService.runAsync`).
The run is no longer using request-time prices, which is the stated stale-price safety property.

**Consequence.** Results can be computed from prices that moved after acceptance, with only a
`WARN`. Low severity, but it silently weakens an explicit guarantee.

**Missing test.** "A snapshot-capture failure is either surfaced to the caller or recorded as a
degraded-mode signal."

---

### F9 — Clock skew

**What happens.** Claims use each worker's local `Instant.now()` for both the fence token and the
staleness comparison. Workers with skewed clocks can consider a fresh claim stale (steal it) or
delay recovery.

**Consequence.** Duplicate execution with the loser fenced out (so no corruption) or slower
recovery. Rare in a single-host deployment; real once the worker is replicated across nodes.

---

## 4. Ranking rationale

The top four are one story: **the accept half has no durable hand-off, and nothing reconciles it.**
F1 creates the stranded row, F6 is the systemic reason it stays stranded, and F2 is why the client
cannot escape on its own — once with a cached 202, once with a 24 h conflict lock. Designing them
together matters: a reconciler without a correct publish path just retries into the same hole, and a
correct publish without a reconciler still loses the crash window between insert and send.

F5 is the same class of bug on the failure half: a terminal state is written before the durable
artifact exists, and the redelivery path treats terminal as "done". It ties with F1 on score and
ranks just below it because the user still sees a failure.

The rest are bounded: F3 duplicates, F8 fails loudly, F7 and F4 slow things down, F10 and F9 are
operational, F15 is a weakened guarantee rather than a lost one.

---

## 5. Prioritized milestones

Each milestone is independently testable and does not require the next one. Milestones 1–3 are the
reliability core; 4–6 are hardening. No implementation is proposed here beyond the shape of the fix.

### M1 — Make the accept half not lose work (F1, F6)

*Shape:* the API must not return a success that has no durable event. Two acceptable designs, in
order of size:

1. **Await the send and compensate.** After inserting `PENDING`, wait for the broker
   acknowledgement before responding. On send failure, move the row to a terminal state (or delete
   it) and return a non-2xx. This closes the ordinary failure; it leaves the crash window between
   insert and send.
2. **Outbox.** Persist the event in the same transaction as the `PENDING` row and relay it. This
   closes the crash window too, at the cost of a new table/relay.

*Acceptance:* with an injected send failure, no request returns 2xx while a `PENDING` row exists
without a durable event; with a crash between insert and send, a restart either delivers the event
or terminates the row.

*Test first:* fault-inject the `KafkaTemplate` send future and assert the pair invariant
(`2xx` ⇔ event durable), then add the crash-window case.

### M2 — Reconcile stranded rows (F6, F1, F2)

*Shape:* a bounded, idempotent sweep that finds `PENDING` rows older than a threshold with no
completion and re-publishes them (safe today: the worker's claim is atomic and duplicates are
skipped). Reuse the existing event; do not add a second execution path.

*Acceptance:* a `PENDING` row whose publish was dropped is re-driven without operator SQL; a
`COMPLETED` row is untouched; a sweep that runs twice delivers at most one execution.

*Test first:* seed a stranded row, run the sweep, assert the event is produced and the row reaches
a terminal state exactly once. Add a "no stale predicate" regression for
`findStaleProcessingSimulations` or replace it.

### M3 — Do not mark terminal before the DLT exists (F5)

*Shape:* either publish to the DLT first and mark `FAILED` after a successful publish, or make the
`FAILED`-with-no-DLT case retryable instead of a blind ack.

*Acceptance:* with a DLT send failure injected, the record is retried to the DLT after the fault is
removed; it is never acknowledged into a state where the row is `FAILED` and the DLT has no copy.

*Test first:* inject a throwing DLT template, deliver a poison record, remove the fault, assert the
DLT receives the payload and the row is `FAILED` only after it does.

### M4 — Make the idempotent retry actually recover (F2, F3)

*Shape:* the cached record must represent the *simulation*, not the 202. Either key the cache by
`simulationId` so a repeat resolves to the same row and can re-drive it, or cache only terminal
states and let a repeat re-enter the accept path (which M1/M2 then make safe).

*Acceptance:* same key after a lost publish re-drives or returns a recoverable error; two accepts
for one client intent produce one simulation; a new key after a `5xx` still cannot double-accept the
same intent.

*Test first:* extend the idempotency tests with a "202 cached, event lost" case, a
"process killed holding the PENDING lock" case, and a "same intent, two keys" case.

### M5 — Throughput and visibility (F7)

*Shape:* enable listener concurrency > 1 (the action PR-D1 gates) and add a consumer-lag gauge so
the backlog is visible and alertable. This is the observability report's future enhancement #3.

*Acceptance:* the disabled concurrency placeholder test passes; a synthetic backlog is visible as a
Prometheus series and crosses an alert threshold.

*Test first:* un-disable `SimulationAsyncUnclaimableConcurrencyPlaceholderTest`; add a lag-gauge
test.

### M6 — Operational hardening (F8, F10, F4, F9, F15)

*Shape:* explicit Redis health/readiness policy; graceful shutdown with a drain timeout; a cap on
the park/resume cycle (or lower log level) so a 12-minute lease does not flood; a documented clock
assumption; and a signal when the snapshot is degraded to live prices.

*Acceptance:* Redis-down produces a deterministic readiness result; SIGTERM mid-run leaves a
recoverable state; a parked partition produces bounded log lines; a snapshot failure is observable.

*Test first:* one test per item, none of which needs the others.

---

## 6. What is already safe (do not "fix")

Worth stating explicitly, because a reliability review that only lists gaps invites re-litigating
working code:

- A stale worker cannot overwrite a reclaimed row (`completeIfClaimHeld` fence).
- A stale worker cannot release a peer's live claim (`releaseIfClaimHeld` fence).
- A duplicate delivery of a completed/failed simulation is acknowledged, not re-run.
- A lost claim mid-run stops silently instead of publishing a stale result.
- A poison payload is dead-lettered with its original bytes and headers preserved.
- A bad-input failure is classified non-retryable instead of burning the retry budget.
- A `PROCESSING` row is never marked `FAILED` by the recoverer.
- The lease/`max.poll.interval.ms` invariant is asserted by a test.

---

## 7. Deployment caveats (not code defects)

- `KafkaConfig` creates topics with `replicas(1)`, and the compose broker sets
  `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1`. Losing the broker loses data. Fine for local dev;
  a production deployment must raise replication to ≥3 before any durability claim above holds.
- The worker has no Redis by design (`spring.cache.type: none`); do not "restore" it to fix F8 —
  the idempotency filter is an API concern.
- The worker currently cannot start in this environment (out-of-band `WORKER_DB_PASSWORD`), which
  is why F4/F5/F7/F10 are designed but not executed here.

---

## 8. Bottom line

The **execution half is genuinely well built**: fenced claims, fenced completion, fenced release,
terminal-state duplicate suppression, a bounded retry budget, and a DLT path with real tests. The
**hand-off between the API and Kafka is the weak point**: the API writes `PENDING`, publishes
fire-and-forget, caches the 202, and has no reconciler. That single seam produces the three
highest-ranked failure modes (F1, F2, F6), and the failure half has one ordering defect of the
same kind (F5).

If only one thing is fixed, fix the seam: **a success response must imply a durable event, and a
stranded row must be recoverable without SQL.** M1–M3 are that fix.
