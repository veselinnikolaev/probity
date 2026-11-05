# M3 — Durable DLT Handling: Design (rev 2)

**Status:** Design only. No production code, migration, configuration, test, commit or push in this change.
**Baseline:** `master` @ `aa15d42` (M2: `feat(reliability): implement reconciliation sweeper`) plus local, unpushed CI fix `ba6f144` (not relevant to this design).
**Date:** 2026-11-04
**Scope:** Design a durable Dead Letter Topic (DLT) path for Probity that (a) preserves an immutable history of every terminal failure, (b) fences the `FAILED` transition to the exact claim attempt that failed, and (c) leaves the original Kafka record recoverable when durable persistence fails.
**Related:** `RELIABILITY_ASSESSMENT.md`, `M1_ACCEPTANCE_SUMMARY.md`, `M2_RECONCILIATION_SWEEPER_DESIGN.md`, `M1_M2_DISCREPANCY_INVESTIGATION.md`, `docs/decisions/0012-transactional-outbox.md`

---

## 0. Final decisions

| # | Decision | Rationale (short) |
|---|---|---|
| **D1** | **Dedicated `simulation_dlt_outbox` table**, one immutable row per terminal failure event. | `simulation_outbox` has `CONSTRAINT uq_simulation_id UNIQUE (simulation_id)` (`V20__simulation_outbox.sql:41`). It physically cannot hold more than one DLT event per simulation, so extending it would overwrite history. |
| **D2** | `dlt_event_id` (primary key) is the immutable event identity; it is never reused or updated. | Each terminal failure gets a distinct, durable identity that survives crashes and replay. |
| **D3** | `simulations.attempt_id UUID` is written by every successful `claimForProcessing`; the `FAILED` transition is fenced on `status = 'PENDING' AND attempt_id = :failedAttemptId`. | This is the smallest fence that proves ownership of the **exact** attempt. Without it, a late recoverer can terminalize a later attempt that already released to `PENDING`. |
| **D4** | The fenced `FAILED` update and the DLT row insert run in **one PostgreSQL transaction** (`REQUIRES_NEW`), owned by a Spring bean. | The transaction must never commit `FAILED` without the corresponding DLT event, and vice versa. A manually constructed recoverer cannot honour `@Transactional`, so the writer is a bean. |
| **D5** | The recoverer does **no Kafka I/O**. It persists the event and returns; a scheduled `DltOutboxPublisher` performs the Kafka send later. | One authoritative DLT flow. `DeadLetterPublishingRecoverer` is removed from the path; `failIfSendResultIsError=false` is not used to hide a failed send. |
| **D6** | `DefaultErrorHandler.setCommitRecovered(true)` is required. | Under `manual_immediate`, a recoverer returning normally does **not** commit the offset unless `commitRecovered` is set. This is also what stops poison records looping forever. |
| **D7** | DLT delivery is **at-least-once**, never exactly-once. `dlt_published_at` means "Kafka acknowledged the send", not "a downstream consumer processed it". | Honest contract; duplicates are possible and are deduplicated by `dlt_event_id`. |
| **D8** | `dlt_published_at IS NULL` is **not** a universal M4 replay prerequisite. | M3 records and publishes; M4 owns replay policy. A published DLT event is still valid history. |

---

## 1. Why the previous revision is superseded

The prior revision recommended "Option A: extend `simulation_outbox`" and left five questions open (U1–U5). Four verified facts invalidate that direction.

1. **`simulation_outbox` is one row per simulation.**
   `V20__simulation_outbox.sql:41` defines `CONSTRAINT uq_simulation_id UNIQUE (simulation_id)`. A second DLT event for the same simulation cannot be inserted without deleting or overwriting the first. The requirement is an **immutable per-failure history**, so Option A is rejected.

2. **The current recoverer is not atomic.**
   `SimulationFailedRecordRecoverer.accept` calls `repository.markFailedIfPending(simulationId)` first (`SimulationFailedRecordRecoverer.java:50-58`) and only then delegates to `DeadLetterPublishingRecoverer` (`:60-72`). `markFailedIfPending` commits on its own; the Kafka send is a separate, non-transactional step. A crash between the two leaves `FAILED` with no durable DLT event.

3. **The current `FAILED` predicate is not attempt-specific.**
   `SimulationJpaRepository.markFailedIfPending` is `WHERE s.id = :id AND s.deleted = false AND s.status = :pendingStatus` (`:149-156`). It has no `attempt_id` and no fence token. A recoverer from an old attempt can mark a later attempt `FAILED` once that later attempt has released back to `PENDING` (proved in §5).

4. **The current Kafka configuration does not commit a recovered offset under `manual_immediate`.**
   `ack-mode: manual_immediate` (`application.yaml:132`); `commitRecovered` defaults to `false` (`FailedRecordProcessor.java:57, 78-88`). A recoverer returning normally therefore leaves the record seeked and redeliverable (§7). For a poison record the listener never runs, so it would loop indefinitely. The M3 design must make the offset commit conditional on successful persistence.

---

## 2. Source-code evidence baseline

Every claim below is from the working tree, not memory.

| Fact | Exact evidence |
|---|---|
| `simulation_outbox` is one row per simulation | `probity-api/src/main/resources/db/migration/V20__simulation_outbox.sql:29-42`, especially `:41` `uq_simulation_id UNIQUE (simulation_id)` |
| Claim writes `status`, `updated_at` | `probity-shared/.../SimulationJpaRepository.java:64-70` |
| Claim returns the fence token, truncated to micros | `probity-shared/.../SimulationRepository.java:131-136`, `:143-145` |
| Release is fenced on `updated_at` | `SimulationJpaRepository.java:119-128` |
| `FAILED` predicate is `PENDING` only, unfenced | `SimulationJpaRepository.java:149-156` |
| Consumer claims, executes, releases on failure, rethrows | `probity-worker/.../SimulationRequestedEventConsumer.java:94-100`, `:129-147` |
| Consumer acks duplicates for `COMPLETED`/`FAILED` | `SimulationRequestedEventConsumer.java:178-202` |
| Recoverer marks `FAILED` then delegates to Kafka | `probity-worker/.../SimulationFailedRecordRecoverer.java:38-73` |
| Error handler uses `DeadLetterPublishingRecoverer`, retries 2, 1s, ×2.0 | `probity-worker/.../KafkaConsumerConfig.java:104-119`, `:121-150` |
| Consumer ack mode and poll size | `probity-worker/src/main/resources/application.yaml:125`, `:129`, `:132` |
| M1 publisher pattern: claim → commit → send → fenced mark | `probity-worker/.../OutboxPublisher.java:89-219` |
| `isAckAfterHandle()` default is `true` | `CommonErrorHandler.java:173-175`; `DefaultErrorHandler.java:127-134` |
| `commitRecovered` default is `false` | `FailedRecordProcessor.java:57, 78-88` |
| `manual_immediate` means `isManualAck=false` | `KafkaMessageListenerContainer.java:872-875` |
| Error-path ack does not queue when `isAnyManualAck && !commitRecovered` | `KafkaMessageListenerContainer.java:2832-2844`, `:3066-3079` |
| Recoverer exception is rethrown, so no commit | `FailedRecordTracker.java:228-237` |
| `commitRecovered(true)` + `MANUAL_IMMEDIATE` commits after successful recovery | `DefaultErrorHandler.java:168`; `SeekUtils.java:229-249` |
| Classifier traverses causes, so a wrapper preserves not-retryable classification | `ExceptionClassifier.java:223-228` (`setTraverseCauses(true)`) |

Spring Kafka version: `3.3.13` (Spring Boot `3.5.11`).

---

## 3. Storage decision: dedicated `simulation_dlt_outbox`

### 3.1 Why a dedicated table

| Requirement | `simulation_outbox` (Option A) | `simulation_dlt_outbox` (chosen) |
|---|---|---|
| Multiple immutable events per simulation | **Impossible**: `uq_simulation_id` (`V20:41`) | Native: `simulation_id` is not unique |
| Per-event claim token / lease / retry count / publication state | Would share a row with M1 lifecycle columns | First-class columns on every event |
| Preserve prior events when a new failure occurs | Overwrites the only row | Appends a new row; old rows untouched |
| M1 publisher isolation | Must add `dlt_*` guards to every M1 query | M1 queries do not see the table at all |
| M2 sweeper isolation | Must add `dlt_topic IS NULL` guards | No interaction |
| Immutability enforcement | Hard: same row is also mutable M1 state | Column-level `UPDATE` grants on lifecycle columns only |
| M4 replay queries | Ambiguous: one row, two meanings | Clean history query by `simulation_id` / `dlt_event_id` |

The only cost of the dedicated table is one extra table and one extra publisher task. Both are small and explicit; the history requirement makes the shared table unworkable.

### 3.2 Schema (proposed `V22__simulation_dlt_outbox.sql`)

```sql
-- =============================================================================
-- V22__simulation_dlt_outbox.sql
-- Durable, immutable per-failure DLT event history (M3).
-- Additive: no data rewrite, no change to simulation_outbox.
-- =============================================================================

-- The attempt that owns a row. Written by every successful claim; retained on
-- release so the recoverer can fence on the exact attempt. Cleared/rotated by
-- the M4 operator reset before a row becomes claimable again.
ALTER TABLE simulations
    ADD COLUMN IF NOT EXISTS attempt_id UUID;

COMMENT ON COLUMN simulations.attempt_id
    IS 'Claim attempt that currently owns (or last owned) this row. Written by claimForProcessing; '
       'retained on release; fenced by the DLT recoverer. M4 reset must rotate/clear it.';

CREATE TABLE IF NOT EXISTS simulation_dlt_outbox (
    -- Immutable event identity. This is the dlt_event_id.
    dlt_event_id UUID NOT NULL,

    -- Null only when the source record cannot be attributed to a simulation
    -- (unparseable key and no deserialisable payload).
    simulation_id UUID,

    -- Claim attempt UUID for listener failures; deterministic synthetic UUID
    -- derived from (topic, partition, offset) for deserialisation/unattributable
    -- failures. Never null.
    attempt_id UUID NOT NULL,

    -- Source Kafka coordinates: traceability and replay identity.
    source_topic VARCHAR(249) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    source_key TEXT,

    -- Original record value. BYTEA, not JSONB: a poison payload may not be JSON.
    -- NULL means the original record value was null (tombstone).
    payload BYTEA,

    exception_type VARCHAR(255) NOT NULL,
    error_description TEXT,
    failed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Publication lifecycle. These are the only columns the worker may UPDATE.
    claim_token UUID,
    claimed_until TIMESTAMPTZ,
    publish_attempt_count INTEGER NOT NULL DEFAULT 0,
    last_publish_error TEXT,
    dlt_published_at TIMESTAMPTZ,

    CONSTRAINT pk_simulation_dlt_outbox PRIMARY KEY (dlt_event_id),

    -- Primary idempotency key: one event per failure attempt of one source
    -- record. Adding attempt_id (rather than the bare source triple) is what
    -- allows a later M4 replay of the same record to create a new event while
    -- still collapsing duplicate recoveries of the same attempt.
    CONSTRAINT uq_simulation_dlt_source_attempt
        UNIQUE (source_topic, source_partition, source_offset, attempt_id)
);

-- Defense-in-depth for attributed rows: one event per claim attempt, even if a
-- bug ever reports the same attempt from two source records. Partial because
-- simulation_id is nullable for unattributable records.
CREATE UNIQUE INDEX IF NOT EXISTS uq_simulation_dlt_attempt
    ON simulation_dlt_outbox (simulation_id, attempt_id)
    WHERE simulation_id IS NOT NULL;

-- Publisher scan: unpublished rows in creation order.
CREATE INDEX IF NOT EXISTS idx_simulation_dlt_outbox_pending
    ON simulation_dlt_outbox (created_at)
    WHERE dlt_published_at IS NULL;

-- History / replay queries by simulation.
CREATE INDEX IF NOT EXISTS idx_simulation_dlt_outbox_simulation
    ON simulation_dlt_outbox (simulation_id, failed_at DESC);

COMMENT ON TABLE simulation_dlt_outbox
    IS 'Immutable history of terminal simulation failures. One row per failure attempt. '
       'Written atomically with the simulations FAILED transition.';

COMMENT ON COLUMN simulation_dlt_outbox.dlt_event_id
    IS 'Immutable DLT event identity. Never reused or updated.';
COMMENT ON COLUMN simulation_dlt_outbox.attempt_id
    IS 'Claim attempt UUID (listener failure) or deterministic synthetic UUID (deserialisation/'
       'unattributable failure). Part of the idempotency key.';
COMMENT ON COLUMN simulation_dlt_outbox.payload
    IS 'Original record value as bytes. Poison payloads are byte-exact from the '
       'DeserializationException header; listener failures use canonical JSON bytes.';
COMMENT ON COLUMN simulation_dlt_outbox.dlt_published_at
    IS 'Kafka acknowledgement only. Does NOT mean a downstream consumer processed the event. '
       'NULL is not a universal replay prerequisite.';
```

### 3.3 Grants (least privilege, immutability by column grant)

The recoverer and the publisher both run in the worker, so `probity_worker` needs `SELECT`, `INSERT`, and `UPDATE` on the **lifecycle columns only**:

```sql
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'probity_worker') THEN
        GRANT SELECT, INSERT ON simulation_dlt_outbox TO probity_worker;
        GRANT UPDATE (claim_token, claimed_until, publish_attempt_count,
                      last_publish_error, dlt_published_at)
            ON simulation_dlt_outbox TO probity_worker;
    END IF;
END $$;
```

This makes `dlt_event_id`, `simulation_id`, `attempt_id`, the source coordinates, `payload`, `exception_type`, `error_description`, `failed_at`, and `created_at` unmodifiable by the worker at the grant level. `simulations.attempt_id` is covered by the existing table-level `GRANT UPDATE ON simulations` (`V18__worker_role_grants.sql:28`); no new grant is needed.

### 3.4 Invariants

1. `dlt_event_id` is generated once (`UUID.randomUUID()`) and never updated.
2. `(source_topic, source_partition, source_offset, attempt_id)` is unique.
3. `(simulation_id, attempt_id)` is unique when `simulation_id IS NOT NULL`.
4. A row is inserted only in the same transaction as the fenced `FAILED` update (or, for an unattributable record, as the only write in that transaction).
5. The only mutable columns are the five publication-lifecycle columns.
6. A row is never deleted by M3. Retention is an operational concern (see §12).

---

## 4. Transaction boundary

### 4.1 The writer bean

The recoverer is currently constructed with `new SimulationFailedRecordRecoverer(...)` inside `KafkaConsumerConfig` (`:129-132`). Spring AOP cannot proxy a manually constructed object, so `@Transactional` on the recoverer itself would be silently ignored. The transaction must live on an injected bean:

```java
@Service
public class SimulationDltEventWriter {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WriteResult recordAttemptFailure(FailureContext ctx) {
        int updated = simulationJpaRepository.markFailedForAttempt(
                ctx.simulationId(),
                SimulationStatus.PENDING.name(),
                SimulationStatus.FAILED.name(),
                ctx.attemptId(),
                now());

        if (updated == 0) {
            // The attempt no longer owns the row: a peer claimed it, it completed,
            // it was already marked FAILED, or it was deleted. Do not create an event.
            return WriteResult.notOwner();
        }

        dltOutboxJpaRepository.insertIfAbsent(ctx);
        return WriteResult.recorded();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WriteResult recordUnclaimedFailure(FailureContext ctx) {
        // No claim attempt exists (deserialisation failure). PENDING is the fence:
        // PROCESSING means a peer is running, so we must not terminalise it.
        int updated = simulationJpaRepository.markFailedIfPending(
                ctx.simulationId(),
                SimulationStatus.PENDING.name(),
                SimulationStatus.FAILED.name(),
                now());

        if (updated == 0) {
            return WriteResult.notOwner();
        }

        dltOutboxJpaRepository.insertIfAbsent(ctx);
        return WriteResult.recorded();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WriteResult recordUnattributableFailure(FailureContext ctx) {
        // No simulation row to update; the durable event is the only write.
        dltOutboxJpaRepository.insertIfAbsent(ctx);
        return WriteResult.recorded();
    }
}
```

`@Transactional(REQUIRES_NEW)` is deliberate: the error handler runs on the Kafka container thread, potentially inside no transaction at all, and must not inherit or join any caller transaction. The method returns only after Spring has committed the transaction (or rolled it back and thrown).

### 4.2 Exact SQL

Fenced `FAILED` for a listener failure:

```sql
UPDATE simulations
   SET status = 'FAILED',
       updated_at = :now
 WHERE id = :simulationId
   AND deleted = FALSE
   AND status = 'PENDING'
   AND attempt_id = :failedAttemptId;
-- 1 row  => this attempt still owns the row; insert the DLT event
-- 0 rows => the attempt lost ownership; do not insert, return normally
```

Unfenced `FAILED` for a deserialisation failure (no attempt exists):

```sql
UPDATE simulations
   SET status = 'FAILED',
       updated_at = :now
 WHERE id = :simulationId
   AND deleted = FALSE
   AND status = 'PENDING';
-- PENDING is the fence: PROCESSING means a peer is in flight.
```

DLT insert (same transaction, idempotent):

```sql
INSERT INTO simulation_dlt_outbox (
    dlt_event_id, simulation_id, attempt_id,
    source_topic, source_partition, source_offset, source_key,
    payload, exception_type, error_description, failed_at, created_at
) VALUES (
    :dltEventId, :simulationId, :attemptId,
    :sourceTopic, :sourcePartition, :sourceOffset, :sourceKey,
    :payload, :exceptionType, :errorDescription, :failedAt, now()
)
ON CONFLICT (source_topic, source_partition, source_offset, attempt_id)
DO NOTHING;
```

The `ON CONFLICT` target is the primary idempotency constraint. A duplicate recovery of the same attempt is a no-op, not an error. The partial `(simulation_id, attempt_id)` unique index is a second line of defence.

### 4.3 Ordering and rollback

1. `UPDATE` the simulation first.
2. If and only if it affected 1 row, `INSERT` the DLT event.
3. Commit.
4. Return to the recoverer.
5. The recoverer returns to Spring Kafka.
6. Spring Kafka commits the offset only if `commitRecovered=true` and the recoverer returned normally.

If step 1 throws, the transaction rolls back; no `FAILED`, no event. If step 2 throws, the transaction rolls back **both** the `FAILED` update and the insert. If the commit fails, the transaction is not durable and the exception propagates. In every failure case the recoverer throws, the offset is not committed, and the record remains redeliverable.

### 4.4 Why `REQUIRES_NEW`, not `REQUIRED`

The error handler is not a business transaction. Joining an outer transaction would delay the commit until the container finishes, which is not guaranteed, and could let the Kafka offset commit race ahead of the DB commit. `REQUIRES_NEW` gives a hard commit point inside `record...Failure`: when the method returns, the `FAILED` state and the DLT event are durable. The recoverer then returns, and only then does Spring Kafka commit the offset.

### 4.5 Behaviour matrix

| Situation | `UPDATE` rows | DLT insert | Transaction | Recoverer | Offset |
|---|---|---|---|---|---|
| Attempt still `PENDING` with matching `attempt_id` | 1 | 1 (or 0 on duplicate) | commits | returns | committed |
| Attempt is `PROCESSING` (peer owns) | 0 | 0 | commits (no-op) | returns | committed |
| Attempt is `COMPLETED` | 0 | 0 | commits (no-op) | returns | committed |
| Row already `FAILED` | 0 | 0 | commits (no-op) | returns | committed |
| Row missing or soft-deleted | 0 | 0 | commits (no-op) | returns | committed |
| Deserialisation failure, row `PENDING` | 1 | 1 (or 0 on duplicate) | commits | returns | committed |
| Deserialisation failure, row `PROCESSING` | 0 | 0 | commits (no-op) | returns | committed |
| DB unavailable / insert throws | — | — | rolls back | **throws** | **not committed** |
| Duplicate recovery, same attempt | 1 (already `FAILED` => 0) | 0 (`ON CONFLICT`) | commits | returns | committed |

The "0 rows, return normally" cases are correct: the current record's failure is not the responsibility of an attempt that no longer owns the row, and the attempt that does own it has its own record and its own recoverer.

---

## 5. Attempt-specific fencing

### 5.1 New claim SQL

`claimForProcessing` must generate and write a new `attempt_id` on every successful claim:

```sql
UPDATE simulations
   SET status = 'PROCESSING',
       attempt_id = :attemptId,
       updated_at = :now
 WHERE id = :id
   AND deleted = FALSE
   AND (status = 'PENDING'
        OR (status = 'PROCESSING' AND updated_at < :staleThreshold));
```

`SimulationRepository.claimForProcessing` returns a `Claim(Instant fenceToken, UUID attemptId)` instead of a bare `Instant`. `attemptId` is generated with `UUID.randomUUID()` before the update; if the update affects 0 rows, the repository returns empty and the attempt id is discarded.

`releaseIfClaimHeld` and `completeIfClaimHeld` are unchanged in their predicates. They **retain** `attempt_id`:

- On release, the row becomes `PENDING` but keeps the attempt id. This is the "last owner" marker the recoverer fences on.
- On completion, the attempt id is harmless historical data.

The M4 operator reset must rotate or clear `attempt_id` **before** making the row claimable again. Otherwise a stale recoverer for the old attempt could still match a later `PENDING` state with the same attempt id.

### 5.2 Propagating the attempt id to the recoverer

The consumer wraps the failure with the attempt id:

```java
public final class SimulationAttemptFailedException extends RuntimeException {
    private final UUID simulationId;
    private final UUID attemptId;
    // constructor sets cause
}
```

In `SimulationRequestedEventConsumer`, the `catch (Exception e)` block becomes:

```java
if (simulationRepository.releaseToPending(simulationId, fence)) {
    log.info("Released simulation {} back to PENDING for redelivery (attempt={})",
            simulationId, claim.attemptId());
} else {
    log.warn("Could not release simulation {}: the claim taken by attempt {} is no longer current",
            simulationId, claim.attemptId());
}
throw new SimulationAttemptFailedException(simulationId, claim.attemptId(), e);
```

The existing `SimulationClaimLostException` branch still acks and does not wrap: a lost claim means a peer owns the row, so there is no failure to record for this attempt.

Spring Kafka's classifier traverses causes (`ExceptionClassifier.java:227`), so wrapping does **not** break `addNotRetryableExceptions` (`KafkaConsumerConfig.java:139-147`). A wrapper around `EmptyPortfolioException` is still classified not-retryable and goes straight to the recoverer; a wrapper around a transient exception still gets the two retries.

The recoverer extracts the attempt id by walking the cause chain (Spring wraps listener exceptions in `ListenerExecutionFailedException`):

```java
private Optional<UUID> findAttemptId(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause()) {
        if (c instanceof SimulationAttemptFailedException w) {
            return Optional.of(w.attemptId());
        }
    }
    return Optional.empty();
}
```

If no wrapper is found **and** the record was not a deserialisation failure, the recoverer must fail closed: throw a `IllegalStateException` rather than fall back to the unfenced `PENDING` path. Falling back would silently reintroduce the A/B race. For a deserialisation failure there is no listener invocation, so the synthetic path is used instead.

### 5.3 The A/B race, reconstructed with exact SQL

This is the race the brief asks to prove. Timestamps are illustrative; the ordering is what matters. `S` is the simulation row. `A` and `B` are two claims of the same Kafka record (or two records for the same simulation).

**Initial state**

```sql
-- S: status = 'PENDING', attempt_id = NULL, updated_at = t0
```

**Step 1 — A claims**

```sql
UPDATE simulations
   SET status = 'PROCESSING', attempt_id = 'A', updated_at = 'tA'
 WHERE id = 'S' AND deleted = FALSE
   AND (status = 'PENDING'
        OR (status = 'PROCESSING' AND updated_at < 'tA' - lease));
-- 1 row. A's fence token is tA; A's attempt id is A.
```

**Step 2 — A fails and releases**

`SimulationRepository.releaseToPending` runs its own `@Transactional` JPA method and commits **before** the consumer rethrows:

```sql
UPDATE simulations
   SET status = 'PENDING', updated_at = 'tA2'
 WHERE id = 'S' AND deleted = FALSE
   AND status = 'PROCESSING' AND updated_at = 'tA';
-- 1 row. attempt_id remains 'A'.
```

A rethrows `SimulationAttemptFailedException(S, A, cause)`.

**Step 3 — B claims**

```sql
UPDATE simulations
   SET status = 'PROCESSING', attempt_id = 'B', updated_at = 'tB'
 WHERE id = 'S' AND deleted = FALSE
   AND (status = 'PENDING'
        OR (status = 'PROCESSING' AND updated_at < 'tB' - lease));
-- 1 row (S was PENDING). B now owns the row.
```

**Step 4 — A's recoverer runs while B is `PROCESSING`**

Current SQL:

```sql
UPDATE simulations
   SET status = 'FAILED', updated_at = now()
 WHERE id = 'S' AND deleted = FALSE AND status = 'PENDING';
-- 0 rows: B holds PROCESSING.
```

Result: **A cannot fail B's in-flight execution.** The current predicate already provides this narrow guarantee.

**Step 5 — B fails and releases**

```sql
UPDATE simulations
   SET status = 'PENDING', updated_at = 'tB2'
 WHERE id = 'S' AND deleted = FALSE
   AND status = 'PROCESSING' AND updated_at = 'tB';
-- 1 row. attempt_id remains 'B'.
```

**Step 6 — A's recoverer runs late**

Current SQL:

```sql
UPDATE simulations
   SET status = 'FAILED', updated_at = now()
 WHERE id = 'S' AND deleted = FALSE AND status = 'PENDING';
-- 1 row: S is PENDING again, and the predicate does not know whose attempt it is.
```

Result: **A marks B's attempt FAILED.** B's retry budget is consumed by a failure that belongs to A. The current system does **not** guarantee attempt ownership across a release.

**Step 7 — the attempt-fenced SQL**

```sql
UPDATE simulations
   SET status = 'FAILED', updated_at = now()
 WHERE id = 'S' AND deleted = FALSE
   AND status = 'PENDING'
   AND attempt_id = 'A';
-- 0 rows: the row is PENDING with attempt_id = 'B'.
```

B's own recoverer uses `attempt_id = 'B'` and matches 1 row. The fence is exactly one extra predicate, written by `claimForProcessing`, retained by `releaseIfClaimHeld`, and presented by the recoverer.

### 5.4 The verified guarantee

| Scenario | Current code | Attempt-fenced code |
|---|---|---|
| A's recoverer while B is `PROCESSING` | 0 rows (safe) | 0 rows (safe) |
| A's recoverer after B released to `PENDING` | **1 row (unsafe)** | 0 rows (safe) |
| A's recoverer after B completed | 0 rows (safe) | 0 rows (safe) |
| A's recoverer after B was marked `FAILED` | 0 rows (safe) | 0 rows (safe) |
| B's own recoverer after B released | 1 row (works) | 1 row (works) |
| Deserialisation failure while a peer is `PROCESSING` | 0 rows (safe) | 0 rows (safe) |
| Deserialisation failure while row is `PENDING` | 1 row (works) | 1 row (works) |

**Verdict:** the current code prevents A from failing B's **in-flight** execution, but does **not** prevent A from failing a later attempt that has already released. The `attempt_id` predicate is the smallest safe fence that proves ownership of the exact attempt. It is one column, one predicate on claim/release, and one predicate on the failure transition.

### 5.5 Deserialisation / poison records

A deserialisation failure never reaches the listener, so no claim attempt exists. The recoverer:

1. Resolves `simulationId` from the record key if it is a UUID; otherwise from the deserialised payload if one is available; otherwise `NULL`.
2. Derives a deterministic synthetic attempt id: `UUID.nameUUIDFromBytes(("dlt:" + topic + ":" + partition + ":" + offset).getBytes(UTF_8))`.
3. If `simulationId` is non-null, calls `recordUnclaimedFailure`, whose fence is `status = 'PENDING'` (no `attempt_id` predicate). `PROCESSING` is excluded, so a peer in flight is never terminalised.
4. If `simulationId` is null, calls `recordUnattributableFailure`, which only inserts the DLT row.
5. The recoverer returns normally; `commitRecovered(true)` commits the offset. The next delivery (if the commit was lost in a crash) re-runs the recoverer, the `UPDATE` affects 0 rows, and `ON CONFLICT` collapses the duplicate. The loop terminates.

The synthetic id is stable across redeliveries, so `(simulation_id, attempt_id)` and `(source_topic, source_partition, source_offset, attempt_id)` both dedupe.

### 5.6 M4 reset contract (recorded now, implemented later)

Any operator action that moves a row from `FAILED` back to `PENDING` must, in the same transaction:

```sql
UPDATE simulations
   SET status = 'PENDING',
       attempt_id = NULL,       -- or a fresh UUID; never the old attempt id
       updated_at = now()
 WHERE id = :simulationId AND status = 'FAILED';
```

Without this, a stale recoverer from the old attempt could match the new `PENDING` state and terminalise the replay attempt. This is the one M4 obligation that M3's fence depends on.

---

## 6. DLT publication flow

### 6.1 One authoritative flow

```
listener failure
   -> SimulationFailedRecordRecoverer
        -> SimulationDltEventWriter (REQUIRES_NEW)
             -> UPDATE simulations ... FAILED (fenced)
             -> INSERT simulation_dlt_outbox ... (ON CONFLICT DO NOTHING)
             -> COMMIT
        -> return
   -> Spring Kafka commits the recovered offset (commitRecovered=true)

scheduled DltOutboxPublisher (independent)
   -> claim row (FOR UPDATE SKIP LOCKED, lease)
   -> COMMIT
   -> kafkaTemplate.send(dltTopic, key, payload).get(timeout)
   -> fenced UPDATE dlt_published_at = now()
```

There is **no** `DeadLetterPublishingRecoverer` in this path. The recoverer performs zero Kafka I/O. The only component that writes to `simulation-requested.DLT` is `DltOutboxPublisher`.

### 6.2 Publisher claim

Mirror `OutboxPublisher.claimNextRecord` (`:112-142`):

```sql
SELECT dlt_event_id, source_topic, source_partition, source_key, payload
  FROM simulation_dlt_outbox
 WHERE dlt_published_at IS NULL
   AND (claimed_until IS NULL OR claimed_until < :now)
 ORDER BY created_at ASC
 FOR UPDATE SKIP LOCKED
 LIMIT :batchSize;
```

Then, in the same short transaction:

```sql
UPDATE simulation_dlt_outbox
   SET claim_token = :claimToken,
       claimed_until = :claimedUntil
 WHERE dlt_event_id = :id
   AND dlt_published_at IS NULL
   AND (claim_token IS NULL OR claimed_until < :now);
```

Commit before any Kafka I/O. The lease must outlive the blocking send:

```java
long leaseMs = Math.max(initialBackoffMs, (sendTimeoutSecs + 5L) * 1000L);
Instant claimedUntil = Instant.now().plusMillis(leaseMs);
```

This is the same formula as `OutboxPublisher:127-129`.

### 6.3 Send

```java
kafkaTemplate.send(dltTopic, sourceKey, payload)
    .get(sendTimeoutSecs, TimeUnit.SECONDS);
```

- `kafkaTemplate` is the existing `deadLetterProducerFactory`-backed template (`KafkaConsumerConfig:82-102`), which already sets `ByteArraySerializer`, `acks=all`, `retries=3`, `max.block.ms=60000`. Rename it `dltKafkaTemplate` and delete the `DeadLetterPublishingRecoverer` bean.
- Key: original `source_key` (usually the simulation id), so per-simulation ordering is preserved by the default partitioner.
- Value: `payload` bytes, byte-exact for poison records.
- Headers: `dlt_event_id`, `original_topic`, `original_partition`, `original_offset`, `exception_type`, `failed_at`, `schema_version`. Do **not** copy the `DeserializationException` header: it contains a serialized exception and is not needed once the bytes are stored in `payload`.

If partition preservation is required, validate at startup that the DLT topic has at least `source_partition + 1` partitions; otherwise let the key-based partitioner choose. Do not fail the send because of a partition-count mismatch.

### 6.4 Fenced mark

```sql
UPDATE simulation_dlt_outbox
   SET dlt_published_at = now(),
       claim_token = NULL,
       claimed_until = NULL
 WHERE dlt_event_id = :id
   AND claim_token = :claimToken
   AND dlt_published_at IS NULL;
```

If 0 rows, the lease lapsed and another publisher owns the row. The other publisher will publish it. At-least-once duplicate is possible and accepted; `dlt_event_id` is the dedupe key.

### 6.5 Failure and backoff

```sql
UPDATE simulation_dlt_outbox
   SET claim_token = NULL,
       claimed_until = :retryAt,
       publish_attempt_count = publish_attempt_count + 1,
       last_publish_error = :error
 WHERE dlt_event_id = :id
   AND claim_token = :claimToken;
```

`retryAt = now + min(maxBackoffMs, initialBackoffMs << min(priorAttempts, 20))`, mirroring `OutboxPublisher.recordFailure` (`:186-219`). The row is never dropped; `dlt_published_at` stays `NULL`. After `maxErrorCount` consecutive failures, log a warning for operator intervention, but keep retrying.

### 6.6 Payload bytes

| Failure type | Payload source | Byte-exact? |
|---|---|---|
| Deserialisation / poison | `DeserializationException.getData()` from the `ErrorHandlingDeserializer` header | **Yes** |
| Listener failure (payload deserialised) | `ObjectMapper.writeValueAsBytes(record.value())` (canonical JSON) | No; semantically equivalent |
| Null value (tombstone) | `NULL` | Yes (null preserved) |
| Unattributable poison | `DeserializationException.getData()` | **Yes** |

If true byte-exactness is required for listener failures too, the consumer must retain the raw bytes (for example, a `BytesDeserializer` plus explicit parsing, or a custom `JsonDeserializer` that stores the raw bytes in a header). That is an optional hardening, not an M3 blocker; the durable history and the poison path are correct without it.

---

## 7. Kafka recovery and offset semantics (verified)

### 7.1 What the current configuration does

Verified facts:

1. `ack-mode: manual_immediate` (`application.yaml:132`).
2. `DefaultErrorHandler` with `ExponentialBackOffWithMaxRetries(2)`, initial 1000 ms, multiplier 2.0 (`KafkaConsumerConfig.java:134-136`).
3. `CommonErrorHandler.isAckAfterHandle()` defaults to `true` (`CommonErrorHandler.java:173-175`); `DefaultErrorHandler` inherits it (`:127-134`).
4. `commitRecovered` defaults to `false` (`FailedRecordProcessor.java:57, 78-88`).
5. Under `manual_immediate`, `isManualAck = false` (`KafkaMessageListenerContainer.java:872-875`).
6. After the error handler returns, `commitOffsetsIfNeededAfterHandlingError` calls `ackCurrent(cRecord, this.isManualAck)` (`:2832-2844`).
7. `ackCurrent` queues an ack only when `!autoCommit && (!isAnyManualAck || commitRecovered)` (`:3066-3079`). With `manual_immediate` and `commitRecovered=false`, it does **not** queue.
8. The failed record is seeked (`seekAfterError` defaults to `true`), so it is redelivered.
9. If the recoverer throws, `FailedRecordTracker.attemptRecovery` rethrows (`:228-237`); `SeekUtils.seekOrRecover` propagates; no commit is reached.

**Consequence:** with the current configuration, a recoverer that returns normally does **not** commit the offset. The record is redelivered. For a listener failure the next delivery can ack via `handleUnclaimable` once the row is `FAILED`. For a deserialisation failure the listener never runs, so the record would be redelivered forever. This is a real gap, not a theoretical one.

### 7.2 Required configuration

```java
DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
errorHandler.setCommitRecovered(true);
errorHandler.addNotRetryableExceptions(...); // unchanged
```

Why this is correct:

- `DefaultErrorHandler.handleRemaining` passes `isCommitRecovered()` into `SeekUtils.seekOrRecover` (`DefaultErrorHandler.java:168`).
- `SeekUtils.seekOrRecover` commits the recovered record's offset **after** `doSeeks` returns `true` and only when the ack mode is `MANUAL_IMMEDIATE` (`SeekUtils.java:229-249`). `doSeeks` returns `true` only after the recoverer returned normally.
- If the recoverer throws, `doSeeks` throws and the commit is skipped. The record remains eligible for redelivery.
- `resetStateOnRecoveryFailure` defaults to `true` (`FailedRecordTracker.java:64`), so a recoverer failure clears the retry state and the record goes through the retry cycle again rather than hot-looping the recoverer. Document that a DB outage during recovery effectively extends the retry budget; that is safe because the simulation is re-run under a new fenced claim.

Do **not** set `failIfSendResultIsError=false` anywhere. With `DeadLetterPublishingRecoverer` removed, the property is irrelevant; leaving it in place would suggest a Kafka send can be "recovered" without a durable DB write.

### 7.3 Recoverer return and throw

| Recoverer outcome | DB state | Offset | Record |
|---|---|---|---|
| Returns normally, `recorded` | `FAILED` + DLT row committed | committed (sync) | done |
| Returns normally, `notOwner` | unchanged (peer owns / terminal) | committed (sync) | done; the owning attempt has its own record |
| Throws (DB unavailable, insert failure, commit failure) | rolled back | **not committed** | redelivered; retry state reset |

The recoverer returns only after the `REQUIRES_NEW` transaction has committed. The offset commit happens after that return. This is the ordering the brief requires.

### 7.4 End-to-end sequences

**Success**

1. Retries exhausted (or non-retryable) → recoverer invoked.
2. `SimulationDltEventWriter.recordAttemptFailure` runs `UPDATE ... WHERE status='PENDING' AND attempt_id=A` → 1 row.
3. `INSERT ... ON CONFLICT DO NOTHING` → 1 row.
4. Commit. `FAILED` and the DLT event are durable.
5. Recoverer returns.
6. Spring Kafka commits the offset (sync).
7. Later, `DltOutboxPublisher` claims the row, sends to `simulation-requested.DLT`, waits for ack, marks `dlt_published_at`.

**Persistence failure**

1. Recoverer invoked.
2. Writer throws (connection lost, constraint violation, commit failure).
3. Transaction rolls back; `FAILED` is not written; no DLT row.
4. Recoverer propagates.
5. `SeekUtils.seekOrRecover` does not reach the commit; the offset is not committed.
6. Retry state is reset (`resetStateOnRecoveryFailure=true`); the record is redelivered and retried.

**Crash after DB commit, before offset commit**

1. `FAILED` + DLT row are durable.
2. Process dies before the offset commit.
3. On restart the record is redelivered.
4. Listener path: `claimForProcessing` affects 0 rows (`FAILED`), `handleUnclaimable` sees `FAILED`, acks.
5. Poison path: the recoverer runs again, the `UPDATE` affects 0 rows, `ON CONFLICT` collapses the duplicate, returns normally, `commitRecovered(true)` commits.
6. The DLT row is published by the scheduled publisher exactly once in the normal case, or more than once if the publisher also crashes between Kafka ack and the `dlt_published_at` update (at-least-once).

**Crash after Kafka ack, before `dlt_published_at` update**

1. The DLT record is on Kafka.
2. The row still has `dlt_published_at = NULL`.
3. On restart the publisher claims it again and re-sends.
4. Two DLT records with the same `dlt_event_id`. Accepted; downstream dedupes on `dlt_event_id`.

### 7.5 No exactly-once claim

`dlt_published_at` is set only after `KafkaTemplate.send(...).get(...)` returns without error, which means the broker acknowledged the produce request under `acks=all`. It does **not** mean a downstream consumer read, processed, or committed the record. M3 makes no exactly-once claim. Duplicates are expected and are the downstream consumer's responsibility, keyed by `dlt_event_id`.

---

## 8. Multiple-attempt semantics

### 8.1 Definitions

- **Claim attempt**: one successful `claimForProcessing`, identified by a fresh `attempt_id`.
- **Terminal failure**: the failure that exhausts the Kafka record's retry budget, or a non-retryable failure, for the attempt that last owned the row.
- **DLT event**: one immutable `simulation_dlt_outbox` row with a fresh `dlt_event_id`.

### 8.2 Rules

1. Every terminal failure attempt gets a **distinct** `dlt_event_id`.
2. A simulation may have **many** DLT rows over its lifetime; `simulation_id` is not unique.
3. The same attempt cannot produce two DLT rows: `(source_topic, source_partition, source_offset, attempt_id)` is unique, and `(simulation_id, attempt_id)` is unique for attributed rows.
4. A duplicate recovery of the same attempt is a no-op (`ON CONFLICT DO NOTHING`); the existing row, its `dlt_event_id`, and its publication state are preserved.
5. Prior rows are never updated except for the five lifecycle columns. A later failure never rewrites an earlier event.
6. A retry cycle (attempts A, B, C on the same Kafka record) produces at most one terminal event: only the attempt that exhausts the budget reaches the recoverer. If a non-retryable failure in A reaches the recoverer first, the row becomes `FAILED`; B and C never run because the next delivery sees `FAILED` and acks.
7. A later, independent failure (for example after an M4 replay) gets a new `attempt_id` and therefore a new `dlt_event_id`. The old row remains.

### 8.3 Worked examples

**Example 1 — retry cycle on one record**

```
record R (offset 10) -> attempt A -> fail -> release (attempt_id=A)
                     -> attempt B -> fail -> release (attempt_id=B)
                     -> attempt C -> fail -> release (attempt_id=C)
                     -> retries exhausted -> recoverer(C)
                     -> FAILED + DLT event E1 (attempt_id=C)
```

One record, three attempts, one terminal event. A's and B's recoverers never run because the retry budget is per record and only C is terminal.

**Example 2 — non-retryable failure in A**

```
record R (offset 10) -> attempt A -> EmptyPortfolioException -> release (attempt_id=A)
                     -> classifier: not retryable -> recoverer(A)
                     -> FAILED + DLT event E1 (attempt_id=A)
```

One attempt, one event. The next delivery sees `FAILED` and acks.

**Example 3 — two independent records for the same simulation**

```
record R1 (offset 10) -> attempt A -> terminal -> E1 (attempt_id=A)
record R2 (offset 20) -> attempt D -> terminal -> E2 (attempt_id=D)
```

Two events, both preserved, both with distinct `dlt_event_id`. This is the "multiple failure attempts per simulation" case.

**Example 4 — duplicate recovery of the same attempt**

```
recoverer(A) -> E1 committed, offset commit lost
recoverer(A) again -> UPDATE 0 rows (already FAILED) -> ON CONFLICT DO NOTHING -> commit
```

One event. The second recovery does not create a duplicate and does not overwrite the publication state.

**Example 5 — M4 replay**

```
E1 (attempt A) published
M4 reset: status=PENDING, attempt_id=NULL
new record R3 (offset 30) -> attempt D -> terminal -> E2 (attempt_id=D)
```

E1 is preserved. E2 is new. `dlt_published_at IS NULL` was never required for the replay; the operator reset the simulation, not the DLT history.

---

## 9. Failure matrix

| # | Scenario | DB result | DLT result | Kafka result | Recovery |
|---|---|---|---|---|---|
| **F1** | Atomic `FAILED` + DLT insert | 1 row `FAILED`; 1 DLT row committed | `dlt_published_at = NULL` | recoverer returns; offset committed | Publisher sends later |
| **F2** | Insert fails | Transaction rolls back; no `FAILED` | No row | Recoverer throws; offset not committed | Redelivery |
| **F3** | Duplicate recovery, same attempt | 0-row update (already `FAILED`); `ON CONFLICT` no-op | Existing row preserved | Recoverer returns; offset committed | None needed |
| **F4** | Stale recoverer A after B released | 0 rows (`attempt_id=B`) | No row | Recoverer returns; offset committed | B's own recoverer handles B |
| **F5** | Stale recoverer A while B `PROCESSING` | 0 rows | No row | Recoverer returns; offset committed | B continues |
| **F6** | Non-retryable failure | 1 row `FAILED` + DLT row | New event | Recoverer returns; offset committed | Publisher sends |
| **F7** | Deserialisation failure, row `PENDING` | 1 row `FAILED` + DLT row | New event, synthetic attempt id | Recoverer returns; offset committed | Publisher sends |
| **F8** | Deserialisation failure, row `PROCESSING` | 0 rows | No row | Recoverer returns; offset committed | Peer owns the row |
| **F9** | Unattributable record (no simulation id) | No simulation write | DLT row with `simulation_id = NULL` | Recoverer returns; offset committed | Publisher sends |
| **F10** | Crash after DB commit, before offset commit | `FAILED` + DLT row durable | Durable | Offset not committed; redelivery | Next delivery acks / dedupes |
| **F11** | Crash after Kafka ack, before `dlt_published_at` | Row still unpublished | Durable | Duplicate DLT record possible | Publisher re-sends; downstream dedupes |
| **F12** | Two publishers | One claims; the other skips | One active send | At most one send per lease | Lease/backoff |
| **F13** | Publisher lease expires mid-send | Row claimable again | Possible duplicate | At-least-once | Fenced mark prevents overwrite |
| **F14** | Row missing / deleted | 0-row update | No row | Recoverer returns; offset committed | Nothing to record |
| **F15** | Row `COMPLETED` | 0-row update | No row | Recoverer returns; offset committed | Terminal success preserved |
| **F16** | M1 outbox publisher | Unaffected | Unaffected | Unaffected | Separate table |
| **F17** | M2 reconciliation sweeper | Unaffected | Unaffected | Unaffected | Separate table |

---

## 10. Test matrix (Testcontainers: PostgreSQL 16 + Kafka 3.7)

| # | Test | Setup | Expected |
|---|---|---|---|
| **T1** | Atomic commit | Attempt A owns `PENDING`; recoverer runs | 1 `FAILED` row; 1 DLT row; same transaction; `dlt_published_at IS NULL` |
| **T2** | Rollback on insert failure | Force the insert to throw | No `FAILED`; no DLT row; recoverer throws; offset not committed |
| **T3** | Duplicate recovery | Run recoverer(A) twice | Exactly one DLT row; `dlt_event_id` unchanged; publication state preserved |
| **T4** | A/B race — B released | A releases; B claims and releases; recoverer(A) runs | 0 rows for A; recoverer(B) later writes 1 row |
| **T5** | A/B race — B in flight | A releases; B claims; recoverer(A) runs | 0 rows; B stays `PROCESSING` |
| **T6** | Non-retryable | `EmptyPortfolioException` in A | Recoverer runs without retries; `FAILED` + 1 DLT row |
| **T7** | Deserialisation failure | Poison payload, row `PENDING` | `FAILED` + 1 DLT row; synthetic attempt id; byte-exact payload; offset committed |
| **T8** | Deserialisation failure while `PROCESSING` | Poison payload, peer owns | 0-row update; no DLT row; offset committed |
| **T9** | Unattributable record | Invalid key + poison payload | DLT row with `simulation_id = NULL`; offset committed |
| **T10** | Crash after DB commit | Kill before offset commit; restart | Redelivery; no duplicate DLT row; offset eventually committed |
| **T11** | Publisher happy path | Claim, send, mark | `dlt_published_at` set; claim token cleared |
| **T12** | Publisher send failure | Broker down | `publish_attempt_count` incremented; `claimed_until` pushed; row remains unpublished |
| **T13** | Concurrent publishers | Two publisher threads | One claim; no double mark; at most one send per lease |
| **T14** | Lease expiry | Publisher stalls past `claimed_until` | Row reclaimable; stale mark affects 0 rows |
| **T15** | Immutable columns | Attempt to update `payload` / `attempt_id` as `probity_worker` | Permission denied |
| **T16** | Multiple attempts | Two records for one simulation, both terminal | Two DLT rows, distinct `dlt_event_id`, both preserved |
| **T17** | M1 regression | `OutboxIntegrationTest` 9/9 | Unchanged |
| **T18** | M2 regression | `ReconciliationIntegrationTest` T1–T16 | Unchanged; no interaction with the new table |

Acceptance gates:

1. T1–T18 pass in CI.
2. T2 proves the offset is not committed when persistence fails.
3. T4 and T5 prove the attempt fence.
4. T7 and T8 prove the poison path terminates and does not touch a `PROCESSING` row.
5. T15 proves immutability at the grant level.
6. T16 proves multiple immutable events per simulation.
7. T17/T18 prove M1/M2 are untouched.

---

## 11. M3 / M4 boundary

**M3 establishes**

- Durable, immutable per-failure DLT history in PostgreSQL.
- An atomic fenced `FAILED` + DLT insert.
- Attempt-specific fencing of the `FAILED` transition.
- At-least-once DLT publication by a scheduled publisher.
- Honest publication semantics: `dlt_published_at` = Kafka ack only.

**M3 does not do**

- Automatic replay or retry policy.
- Exactly-once Kafka delivery.
- A universal "only replay if `dlt_published_at IS NULL`" rule.
- Retention/archival of DLT rows.
- A DLT consumer. M3 only produces the DLT topic.

**M4 obligations inherited from this design**

1. Any `FAILED → PENDING` reset must rotate or clear `simulations.attempt_id` in the same transaction (§5.6).
2. Replay must create a new claim attempt (new `attempt_id`) and therefore a new DLT event if it fails again.
3. Replay must not use `dlt_published_at IS NULL` as a prerequisite. A published event is still valid history.
4. If replay re-delivers the **same** Kafka record, the composite key includes `attempt_id`, so a new attempt still creates a new event. The old event is never overwritten.

---

## 12. Remaining risks

| # | Risk | Severity | Mitigation / status |
|---|---|---|---|
| **R1** | `@Transactional` on a manually constructed recoverer is ignored. | High if missed | The transaction lives on the injected `SimulationDltEventWriter` bean, not on the recoverer. The recoverer is still constructed in `KafkaConsumerConfig`, but the writer is a bean parameter. Add a test that the writer is proxied. |
| **R2** | Attempt-id extraction from the cause chain fails because Spring wraps the listener exception. | High if missed | Walk the full cause chain for `SimulationAttemptFailedException`; if absent on a non-deserialisation failure, **throw** (fail closed) rather than fall back to the unfenced path. |
| **R3** | Listener-failure payloads are re-serialized, not byte-exact. | Low | Poison payloads are byte-exact from the `DeserializationException` header. If byte-exactness is required for all failures, add a raw-bytes-preserving deserializer. |
| **R4** | `commitRecovered(true)` only commits under `MANUAL_IMMEDIATE`. | Medium | The ack mode is already `manual_immediate`; add a startup assertion or a test that fails if the mode changes. If the mode changes to `MANUAL`, the record is redelivered and the next delivery acks; correctness is preserved, liveness is worse. |
| **R5** | A DB outage during recovery resets the retry state, so the simulation is re-executed. | Medium | Safe because execution is fenced and idempotent; document the extended retry budget. Alternatively set `resetStateOnRecoveryFailure=false` and accept a tighter recoverer retry loop. |
| **R6** | `dlt_published_at` is misinterpreted as "downstream processed". | Medium | Documented in the schema comment and §7.5. Downstream must dedupe on `dlt_event_id`. |
| **R7** | M4 reset forgets to rotate `attempt_id`, so a stale recoverer can match. | High if missed | Written into the M4 contract (§5.6) and covered by a future M4 test. |
| **R8** | Retention: DLT rows are never deleted in M3, so the table grows. | Low | Same policy as M1: indefinite retention, operational archival in a later phase. Add an index on `created_at` for future archival. |
| **R9** | Unattributable records (`simulation_id = NULL`) are a new class of DLT row. | Low | Explicitly modelled; the source-attempt key dedupes them. Operators must know they exist. |
| **R10** | The `simulation_outbox` M1 publisher and the DLT publisher both run in the worker and could contend for DB connections. | Low | Separate short transactions; same pool. Monitor pool saturation. |
| **R11** | `ON CONFLICT` masks a genuinely different event that happens to share the same `(source, attempt)` key. | Low | The key is the definition of the same event. A different attempt has a different `attempt_id`; a different record has different source coordinates. |

**Overall risk:** **Medium.** The storage and fencing decisions are grounded in verified source code. The two high-severity items (R1, R2) are implementation-discipline risks with explicit fail-closed rules; R7 is a cross-phase contract that must be enforced in M4.

---

## 13. Files inspected

| File | Why |
|---|---|
| `probity-worker/src/main/java/me/veselin/probity/simulation/event/SimulationRequestedEventConsumer.java` | Claim, release, rethrow, `handleUnclaimable`, requeue |
| `probity-worker/src/main/java/me/veselin/probity/simulation/config/SimulationFailedRecordRecoverer.java` | Current non-atomic mark-then-delegate behaviour |
| `probity-worker/src/main/java/me/veselin/probity/common/config/KafkaConsumerConfig.java` | Error handler, DLT recoverer, retry/backoff, producer factory |
| `probity-worker/src/main/resources/application.yaml` | `manual_immediate`, `max.poll.records`, `ErrorHandlingDeserializer` |
| `probity-worker/src/main/java/me/veselin/probity/simulation/service/OutboxPublisher.java` | Claim → commit → send → fenced mark pattern to mirror |
| `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationJpaRepository.java` | Exact claim/release/fail SQL |
| `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationRepository.java` | Fence-token truncation, `markFailedIfPending`, `claimForProcessing` |
| `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationJpaEntity.java` | Entity columns; where `attempt_id` is added |
| `probity-api/src/main/resources/db/migration/V20__simulation_outbox.sql` | `uq_simulation_id` (line 41), grant patterns |
| `probity-api/src/main/resources/db/migration/V21__simulation_reconciliation.sql` | Additive migration pattern |
| `probity-api/src/main/resources/db/migration/V18__worker_role_grants.sql` | Table-level `UPDATE ON simulations` covers the new column |
| `probity-worker/src/test/java/me/veselin/probity/simulation/async/ReconciliationIntegrationTest.java` | M2 regression suite T1–T16 |
| Spring Kafka `3.3.13` sources (`CommonErrorHandler`, `DefaultErrorHandler`, `FailedRecordProcessor`, `FailedRecordTracker`, `KafkaMessageListenerContainer`, `SeekUtils`, `ExceptionClassifier`) | Verified offset/commit/recovery semantics |

## 14. Files changed

- **Modified:** `docs/plans/M3_DURABLE_DLT_DESIGN.md` (this document, rev 2).
- **No application code, migration, configuration, test, commit, or push.**

---

## 15. Final report against the brief

**Final storage decision.** A dedicated `simulation_dlt_outbox` table, one immutable row per terminal failure event, with `dlt_event_id` as the primary key. `simulation_outbox` is rejected because `uq_simulation_id` (`V20:41`) makes per-simulation history impossible. `simulation_id` is nullable only for unattributable records. Idempotency is `(source_topic, source_partition, source_offset, attempt_id)` plus a partial `(simulation_id, attempt_id)` unique index. The worker's `UPDATE` grant is limited to the five publication-lifecycle columns, so event content is immutable at the grant level.

**Transaction boundary.** One `REQUIRES_NEW` PostgreSQL transaction in the injected `SimulationDltEventWriter` bean: fenced `UPDATE simulations ... SET status='FAILED' WHERE status='PENDING' AND attempt_id=:attemptId` first; if and only if it affects 1 row, `INSERT INTO simulation_dlt_outbox ... ON CONFLICT DO NOTHING`; commit. Any failure rolls back both and propagates, so the Kafka offset is not committed.

**DLT publication flow.** The recoverer does no Kafka I/O. A scheduled `DltOutboxPublisher` claims unpublished rows with `FOR UPDATE SKIP LOCKED` + a lease, commits, sends the stored bytes to `simulation-requested.DLT` with `KafkaTemplate.send(...).get(...)`, and marks `dlt_published_at` with a fenced update. `DeadLetterPublishingRecoverer` is removed from the path. `failIfSendResultIsError=false` is not used.

**Multiple-attempt semantics.** A new `attempt_id` is generated on every successful claim and retained on release. Each terminal attempt gets a distinct `dlt_event_id`. The same attempt cannot create two rows. Prior rows and their publication state are never overwritten. A simulation can accumulate any number of immutable DLT events.

**Verified fencing guarantee.** Current code: A cannot fail B while B is `PROCESSING` (0 rows), but A **can** fail B after B releases to `PENDING`, because `markFailedIfPending` only checks `status='PENDING'`. The attempt-fenced predicate `status='PENDING' AND attempt_id=:A` returns 0 rows after B claims or releases (the row carries `attempt_id=B`), and 1 row only for B's own recoverer. This is the smallest safe attempt-specific fence.

**Kafka recovery.** With `commitRecovered(true)` under `manual_immediate`, Spring Kafka commits the recovered record's offset only after the recoverer returns normally, which is only after the `REQUIRES_NEW` transaction commits (`SeekUtils.java:229-249`). If the recoverer throws, the commit is skipped and the record is redelivered (`FailedRecordTracker.java:228-237`). The scheduled publisher is independent of the consumer offset. `dlt_published_at` means Kafka acknowledged the send only; it is not a downstream-processing marker and is not a universal M4 replay prerequisite.
