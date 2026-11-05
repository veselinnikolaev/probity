# Durable DLT Handling (M3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement M3 durable, immutable DLT history with attempt-specific fencing, atomic failure persistence, and at-least-once publication to `simulation-requested.DLT`.

**Architecture:** Add an additive `simulation_dlt_outbox` Flyway migration and an `attempt_id` claim fence on `simulations`. An injected transactional writer atomically marks the exact failed attempt `FAILED` and inserts one immutable DLT event; a DB-only recoverer invokes that writer and Spring Kafka commits the recovered offset only after persistence succeeds. A scheduled publisher mirrors the existing transactional-outbox lease/send/fenced-mark pattern, but publishes stored bytes and lifecycle metadata from the dedicated DLT table.

**Tech Stack:** Java 21, Spring Boot 3.5.11, Spring Data JPA, PostgreSQL/Flyway, Spring Kafka 3.3.13, Jackson, Micrometer, Testcontainers PostgreSQL/Kafka, JUnit 5.

**Spec:** `docs/plans/M3_DURABLE_DLT_DESIGN.md`

## Global Constraints

- Preserve immutable DLT history: one `simulation_dlt_outbox` row per terminal failure and a fresh `dlt_event_id` per attempt.
- Fence listener failures with `simulations.attempt_id`; stale recoverers must fail closed and must not mark a later attempt `FAILED`.
- Keep the fenced `FAILED` update and DLT insert in one Spring `REQUIRES_NEW` transaction owned by an injected bean.
- The recoverer performs no Kafka I/O; only `DltOutboxPublisher` writes `simulation-requested.DLT`.
- DLT delivery is at-least-once; `dlt_published_at` means Kafka acknowledgement only.
- `DefaultErrorHandler.setCommitRecovered(true)` is required with the existing `manual_immediate` ack mode.
- Preserve the original Kafka record when persistence fails: the recoverer must throw and the offset must remain uncommitted.
- Do not delete or rewrite existing M1/M2 outbox rows; M1/M2 regression suites must remain green.
- Do not implement M4 replay, DLT consumption, retention, or exactly-once delivery.

## Review Focus

- A stale recoverer from attempt A after attempt B has released must affect zero rows; cover in the A/B race integration test.
- A database/insert failure must roll back both `FAILED` and the DLT insert and must throw from recovery; cover with a forced insert failure.
- A poison/deserialization record must preserve `DeserializationException.getData()`, use a stable synthetic attempt id, and terminate recovery; cover with attributed and unattributed poison tests.
- A publisher lease expiry must prevent a stale publisher from marking a row published while allowing a later publisher to retry; cover with concurrent/lease tests.
- DLT immutable columns must not be writable by the worker role while lifecycle columns remain writable; cover with the PostgreSQL grant test when the role is available.

---

### Task 1: Add the durable DLT schema and persistence model

**Files:**
- Create: `probity-api/src/main/resources/db/migration/V22__simulation_dlt_outbox.sql`
- Create: `probity-worker/src/test/resources/db/migration/V22__simulation_dlt_outbox.sql`
- Create: `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationDltOutboxJpaEntity.java`
- Create: `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationDltOutboxJpaRepository.java`
- Modify: `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationJpaEntity.java`
- Modify: `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationJpaRepository.java`

**Interfaces:**
- Consumes: Existing `simulations` schema, Flyway V20/V21 conventions, and Spring Data JPA.
- Produces: `SimulationJpaEntity.attemptId`, a DLT entity with immutable event fields and mutable publication lifecycle fields, and native repository operations for atomic event insertion, publisher claiming, fenced success, and fenced failure.

- [ ] **Step 1: Write the failing persistence tests**

Add repository-level coverage to the worker integration test support for:

```java
assertThat(simulationJpaRepository.findById(simulationId).orElseThrow().getAttemptId())
        .isEqualTo(attemptId);
assertThat(dltRepository.findAll()).hasSize(1);
assertThat(dltRepository.findAll().get(0).getPayload()).containsExactly(payload);
```

Pin the uniqueness rules `(source_topic, source_partition, source_offset, attempt_id)` and partial `(simulation_id, attempt_id)`; a duplicate insert must leave the original `dlt_event_id` and publication state unchanged.

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
.\mvnw.cmd -pl probity-worker -am -Dtest=SimulationDltOutboxPersistenceIntegrationTest test
```

Expected: compilation or test failure because the migration, entity, and repository operations do not yet exist.

- [ ] **Step 3: Add the V22 production and worker-test migrations**

Create `V22__simulation_dlt_outbox.sql` with:

```sql
ALTER TABLE simulations ADD COLUMN IF NOT EXISTS attempt_id UUID;

CREATE TABLE IF NOT EXISTS simulation_dlt_outbox (
    dlt_event_id UUID NOT NULL,
    simulation_id UUID,
    attempt_id UUID NOT NULL,
    source_topic VARCHAR(249) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    source_key TEXT,
    payload BYTEA,
    exception_type VARCHAR(255) NOT NULL,
    error_description TEXT,
    failed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claim_token UUID,
    claimed_until TIMESTAMPTZ,
    publish_attempt_count INTEGER NOT NULL DEFAULT 0,
    last_publish_error TEXT,
    dlt_published_at TIMESTAMPTZ,
    CONSTRAINT pk_simulation_dlt_outbox PRIMARY KEY (dlt_event_id),
    CONSTRAINT uq_simulation_dlt_source_attempt
        UNIQUE (source_topic, source_partition, source_offset, attempt_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_simulation_dlt_attempt
    ON simulation_dlt_outbox (simulation_id, attempt_id)
    WHERE simulation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_simulation_dlt_outbox_pending
    ON simulation_dlt_outbox (created_at)
    WHERE dlt_published_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_simulation_dlt_outbox_simulation
    ON simulation_dlt_outbox (simulation_id, failed_at DESC);
```

Add the schema comments and conditional `probity_worker` grants from §3.3 of the M3 design. Keep the worker test migration byte-for-byte equivalent to the API migration so `ddl-auto=validate` and integration tests use the same schema.

- [ ] **Step 4: Add the attempt field and DLT JPA entity**

Add to `SimulationJpaEntity`:

```java
@Column(name = "attempt_id")
private UUID attemptId;
```

Create `SimulationDltOutboxJpaEntity` with UUID, nullable simulation id, non-null attempt id, source coordinates, nullable `byte[] payload`, failure metadata, and publisher lifecycle fields. Map `payload` with `@JdbcTypeCode(SqlTypes.BINARY)` and map timestamps as `Instant`. Keep event fields writable only through the dedicated insert query; expose lifecycle setters only as needed by JPA.

- [ ] **Step 5: Add native repository operations**

Implement these exact operations in `SimulationDltOutboxJpaRepository`:

```java
@Modifying
@Transactional
@Query(value = """
    INSERT INTO simulation_dlt_outbox (
        dlt_event_id, simulation_id, attempt_id, source_topic, source_partition,
        source_offset, source_key, payload, exception_type, error_description,
        failed_at, created_at
    ) VALUES (
        :dltEventId, :simulationId, :attemptId, :sourceTopic, :sourcePartition,
        :sourceOffset, :sourceKey, :payload, :exceptionType, :errorDescription,
        :failedAt, now()
    )
    ON CONFLICT (source_topic, source_partition, source_offset, attempt_id)
    DO NOTHING
    """, nativeQuery = true)
int insertIfAbsent(...);
```

Add `lockPendingBatch`, `claim`, `markPublished`, `recordFailure`, and lookup methods using `FOR UPDATE SKIP LOCKED`, `claim_token`, `claimed_until`, and fenced lifecycle updates. Add `SimulationJpaRepository.claimForProcessing(..., UUID attemptId, ...)` and `markFailedForAttempt(..., UUID attemptId, ...)`.

- [ ] **Step 6: Run the persistence tests**

Run the focused persistence test again and then the existing M1/M2 worker integration selectors. Expected: schema validation passes, duplicate inserts are idempotent, event fields are retained, and existing tests remain green.

- [ ] **Step 7: Commit the schema/persistence slice**

```powershell
git add probity-api/src/main/resources/db/migration/V22__simulation_dlt_outbox.sql probity-worker/src/test/resources/db/migration/V22__simulation_dlt_outbox.sql probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationDltOutboxJpaEntity.java probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationDltOutboxJpaRepository.java probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationJpaEntity.java probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationJpaRepository.java
git commit -m "feat: add durable simulation DLT storage"
```

Include the required Copilot co-author trailer if committing.

### Task 2: Add attempt-specific claim and failure fencing

**Files:**
- Modify: `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationRepository.java`
- Modify: `probity-worker/src/main/java/me/veselin/probity/simulation/event/SimulationRequestedEventConsumer.java`
- Create: `probity-worker/src/main/java/me/veselin/probity/simulation/exception/SimulationAttemptFailedException.java`
- Modify: existing claim/retry integration tests under `probity-worker/src/test/java/me/veselin/probity/simulation/async/`

**Interfaces:**
- Consumes: `SimulationJpaRepository` attempt-aware queries from Task 1.
- Produces: `SimulationRepository.Claim(Instant fenceToken, UUID attemptId)`, attempt-aware `releaseToPending`, and a listener exception carrying `simulationId` plus `attemptId`.

- [ ] **Step 1: Write the failing A/B fencing tests**

Add tests that create a simulation, claim/release it with attempt A, claim/release it with attempt B, then invoke the A failure path and assert:

```java
assertThat(simulationRepository.markFailedForAttempt(simulationId, attemptA)).isFalse();
assertThat(simulationRepository.findLiveStatus(simulationId))
        .contains(SimulationStatus.PENDING);
assertThat(simulationRepository.markFailedForAttempt(simulationId, attemptB)).isTrue();
```

Also assert a fresh B `PROCESSING` row is not terminalized by A.

- [ ] **Step 2: Run the race tests and verify failure**

Run the new selectors with Testcontainers. Expected: the existing bare `Instant` claim API cannot satisfy the attempt-fence assertions.

- [ ] **Step 3: Change claim and release contracts**

Generate `UUID attemptId = UUID.randomUUID()` before the claim update. Update the JPQL claim to write `attemptId`, return a `Claim` record only when one row is updated, and retain `attemptId` on release and completion. Change the consumer to pass `claim.fenceToken()` and `claim.attemptId()`.

- [ ] **Step 4: Add the fail-closed exception wrapper**

Create:

```java
public final class SimulationAttemptFailedException extends RuntimeException {
    private final UUID simulationId;
    private final UUID attemptId;

    public SimulationAttemptFailedException(UUID simulationId, UUID attemptId, Throwable cause) {
        super(cause);
        this.simulationId = simulationId;
        this.attemptId = attemptId;
    }

    public UUID simulationId() { return simulationId; }
    public UUID attemptId() { return attemptId; }
}
```

In the consumer failure branch, release using the fence token and rethrow this wrapper. Preserve the existing `SimulationClaimLostException` acknowledgement path.

- [ ] **Step 5: Run claim/retry regression tests**

Run the existing fenced-complete, retry-budget, poison, and split-write integration tests plus the new A/B tests. Expected: current claim/release behavior remains intact while stale attempts no longer terminalize later attempts.

- [ ] **Step 6: Commit the fencing slice**

```powershell
git add probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationRepository.java probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationJpaRepository.java probity-worker/src/main/java/me/veselin/probity/simulation/event/SimulationRequestedEventConsumer.java probity-worker/src/main/java/me/veselin/probity/simulation/exception/SimulationAttemptFailedException.java probity-worker/src/test/java/me/veselin/probity/simulation/async
git commit -m "feat: fence simulation failures by attempt"
```

### Task 3: Implement the transactional DLT writer

**Files:**
- Create: `probity-worker/src/main/java/me/veselin/probity/simulation/dlt/FailureContext.java`
- Create: `probity-worker/src/main/java/me/veselin/probity/simulation/dlt/WriteResult.java`
- Create: `probity-worker/src/main/java/me/veselin/probity/simulation/dlt/SimulationDltEventWriter.java`
- Modify: `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationRepository.java`
- Create: `probity-worker/src/test/java/me/veselin/probity/simulation/async/SimulationDltEventWriterIntegrationTest.java`

**Interfaces:**
- Consumes: Kafka record metadata, exception metadata, attempt-aware simulation repository operations, and DLT insert repository operations.
- Produces: `recordAttemptFailure(FailureContext)`, `recordUnclaimedFailure(FailureContext)`, and `recordUnattributableFailure(FailureContext)`, each returning `WriteResult.RECORDED` or `WriteResult.NOT_OWNER`.

- [ ] **Step 1: Write atomicity and rollback tests**

Cover:

```java
WriteResult result = writer.recordAttemptFailure(contextFor(simulationId, attemptId));
assertThat(result).isEqualTo(WriteResult.RECORDED);
assertThat(simulationRepository.findLiveStatus(simulationId))
        .contains(SimulationStatus.FAILED);
assertThat(dltRepository.findBySimulationId(simulationId)).hasSize(1);
```

Use a test repository/failure hook to make the DLT insert throw and assert both the simulation status and DLT row remain unchanged. Add duplicate recovery assertions that the first `dlt_event_id` and `dlt_published_at` are preserved.

- [ ] **Step 2: Run the writer tests and verify failure**

Run:

```powershell
.\mvnw.cmd -pl probity-worker -am -Dtest=SimulationDltEventWriterIntegrationTest test
```

Expected: failure because no transactional writer exists.

- [ ] **Step 3: Implement the value types**

Define `FailureContext` as an immutable record containing `UUID dltEventId`, nullable `UUID simulationId`, non-null `UUID attemptId`, source topic/partition/offset/key, nullable payload bytes, exception type/description, and `Instant failedAt`. Ensure byte arrays are defensively copied at construction/access if the project’s value-object conventions permit; never mutate stored event bytes.

Define `WriteResult` as an enum with `RECORDED` and `NOT_OWNER`.

- [ ] **Step 4: Implement the Spring writer bean**

Create `@Service` `SimulationDltEventWriter` with:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public WriteResult recordAttemptFailure(FailureContext context)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public WriteResult recordUnclaimedFailure(FailureContext context)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public WriteResult recordUnattributableFailure(FailureContext context)
```

For attributed listener failures, call `markFailedForAttempt` first and insert only when it updates one row. For deserialization failures, call the PENDING-only mark method. For unattributable records, insert only. Let persistence/commit exceptions propagate; never convert them into a success-shaped result.

- [ ] **Step 5: Run atomicity and proxy tests**

Assert the writer is a Spring bean with transactional advice and run all writer tests. Expected: successful writes commit both records, not-owner results do not insert, and insert failures roll back the status update.

- [ ] **Step 6: Commit the writer slice**

```powershell
git add probity-worker/src/main/java/me/veselin/probity/simulation/dlt probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationRepository.java probity-worker/src/test/java/me/veselin/probity/simulation/async/SimulationDltEventWriterIntegrationTest.java
git commit -m "feat: persist simulation failures atomically"
```

### Task 4: Replace Kafka recovery with the DB-only recoverer

**Files:**
- Modify: `probity-worker/src/main/java/me/veselin/probity/simulation/config/SimulationFailedRecordRecoverer.java`
- Modify: `probity-worker/src/main/java/me/veselin/probity/common/config/KafkaConsumerConfig.java`
- Create/modify: worker recovery integration tests under `probity-worker/src/test/java/me/veselin/probity/simulation/async/`

**Interfaces:**
- Consumes: `SimulationDltEventWriter`, `ObjectMapper`, `ConsumerRecord`, Spring Kafka exception causes, and ErrorHandlingDeserializer headers.
- Produces: a `ConsumerRecordRecoverer` that does zero Kafka I/O, persists one event or no-ops when not owner, and throws on persistence failure.

- [ ] **Step 1: Write recoverer tests**

Cover listener failures, not-retryable wrapper classification, duplicate recovery, missing attempt wrapper fail-closed behavior, attributed poison records, and unattributable poison records. Assert that recovery does not call a Kafka template and that persistence errors propagate.

- [ ] **Step 2: Run the recovery tests and verify failure**

Run the focused recovery selectors. Expected: the current recoverer still depends on `DeadLetterPublishingRecoverer` and performs Kafka I/O.

- [ ] **Step 3: Extract attempt and simulation identity**

Walk the full cause chain to find `SimulationAttemptFailedException`. For non-deserialization failures without that wrapper, throw `IllegalStateException`. For deserialization failures, derive `attemptId` with:

```java
UUID.nameUUIDFromBytes(
        ("dlt:" + record.topic() + ":" + record.partition() + ":" + record.offset())
                .getBytes(StandardCharsets.UTF_8));
```

Resolve the simulation id from a UUID record key, then any available deserialized value; leave it null when attribution is impossible.

- [ ] **Step 4: Extract payload bytes and build `FailureContext`**

For poison records, read the original bytes from the `DeserializationException` header/data. For listener failures, serialize the value with `ObjectMapper`; preserve null tombstones as null. Copy source metadata and include the exception class name, bounded error description, and current timestamp.

- [ ] **Step 5: Wire the three writer paths**

Invoke `recordAttemptFailure`, `recordUnclaimedFailure`, or `recordUnattributableFailure` according to the extracted context. Log `NOT_OWNER` at debug level, log successful durable recording, and rethrow all writer exceptions. Remove `DeadLetterPublishingRecoverer`, its bean, the old DLT publish-failure counter, and the old record coercion/delegation path.

- [ ] **Step 6: Set recovered-offset commit behavior**

Construct `DefaultErrorHandler` with the DB-only recoverer and existing backoff/classifier, then call:

```java
errorHandler.setCommitRecovered(true);
```

Delete the `DeadLetterPublishingRecoverer` configuration and retain the existing producer factory only as the byte-array template needed by the future publisher. Do not configure `failIfSendResultIsError=false`.

- [ ] **Step 7: Run Kafka recovery tests**

Run the poison, retry-budget, and new recovery tests. Expected: successful persistence returns and commits under `manual_immediate`; writer failures throw and do not commit; poison records terminate instead of looping.

- [ ] **Step 8: Commit the recovery slice**

```powershell
git add probity-worker/src/main/java/me/veselin/probity/simulation/config/SimulationFailedRecordRecoverer.java probity-worker/src/main/java/me/veselin/probity/common/config/KafkaConsumerConfig.java probity-worker/src/test/java/me/veselin/probity/simulation/async
git commit -m "feat: persist Kafka failures before recovery"
```

### Task 5: Implement scheduled DLT publication

**Files:**
- Create: `probity-worker/src/main/java/me/veselin/probity/simulation/service/DltOutboxPublisher.java`
- Modify: `probity-worker/src/main/java/me/veselin/probity/common/config/KafkaConsumerConfig.java`
- Create: `probity-worker/src/test/java/me/veselin/probity/simulation/async/DltOutboxPublisherIntegrationTest.java`
- Modify: `probity-worker/src/main/resources/application.yaml`

**Interfaces:**
- Consumes: DLT persistence repository operations, byte-array `KafkaTemplate`, transaction manager, and topic/send/backoff properties.
- Produces: scheduled claim → commit → send → fenced-mark behavior with at-least-once delivery and retry backoff.

- [ ] **Step 1: Write publisher tests**

Cover happy path, broker/send failure, concurrent publishers, lease expiry, stale success mark, metadata headers, null payloads, and retry backoff:

```java
publisher.publishPendingEvents();
assertThat(dltRepository.findById(eventId).orElseThrow().getDltPublishedAt())
        .isNotNull();
```

For a send failure, assert `publish_attempt_count` increments, `dlt_published_at` remains null, and `claimed_until` moves into the future.

- [ ] **Step 2: Run publisher tests and verify failure**

Run the focused publisher selector. Expected: no publisher bean or DLT lifecycle operations exist.

- [ ] **Step 3: Add DLT publisher configuration**

Rename or expose the existing byte-array producer factory/template as a dedicated `dltKafkaTemplate`, preserving `StringSerializer`, `ByteArraySerializer`, `acks=all`, retries, and max block time. Add configurable DLT topic, batch size, poll delay, send timeout, initial/max backoff, and warning threshold properties with defaults matching the existing outbox publisher.

- [ ] **Step 4: Implement claim and lease**

Create a conditional `@Service` `DltOutboxPublisher` with `@Scheduled`. Use a `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW` to lock one unpublished row using `FOR UPDATE SKIP LOCKED`, assign a UUID claim token, and set a lease at least `(sendTimeoutSecs + 5) * 1000` milliseconds or the configured initial backoff, whichever is larger. Commit before Kafka I/O.

- [ ] **Step 5: Implement send and headers**

Send stored `payload` bytes using the original `source_key` to the configured DLT topic and block on `get(sendTimeoutSecs, TimeUnit.SECONDS)`. Add headers for `dlt_event_id`, original topic/partition/offset, exception type, failed timestamp, and schema version. Do not copy serialized deserialization exception headers.

- [ ] **Step 6: Implement fenced success/failure**

On success, update only when `claim_token` still matches and `dlt_published_at IS NULL`; set publication time and clear the lease. On failure, increment `publish_attempt_count`, store a bounded error message, and set `claimed_until` to exponential retry time. Keep retrying forever and warn after the configured threshold; never delete the row.

- [ ] **Step 7: Run publisher and regression tests**

Run the publisher integration tests, then `OutboxIntegrationTest` and `ReconciliationIntegrationTest`. Expected: DLT rows publish independently and M1/M2 outbox behavior remains unchanged.

- [ ] **Step 8: Commit the publisher slice**

```powershell
git add probity-worker/src/main/java/me/veselin/probity/simulation/service/DltOutboxPublisher.java probity-worker/src/main/java/me/veselin/probity/common/config/KafkaConsumerConfig.java probity-worker/src/main/resources/application.yaml probity-worker/src/test/java/me/veselin/probity/simulation/async/DltOutboxPublisherIntegrationTest.java
git commit -m "feat: publish durable DLT events"
```

### Task 6: Complete M3 integration coverage and documentation

**Files:**
- Create/modify: `probity-worker/src/test/java/me/veselin/probity/simulation/async/SimulationDltIntegrationTest.java`
- Modify: `probity-api/src/test/java/me/veselin/probity/simulation/config/SimulationWorkerConfigInvariantTest.java`
- Modify: `README.md` or worker operational documentation only if the existing project location documents Kafka worker behavior
- Modify: `docs/plans/M3_DURABLE_DLT_DESIGN.md` only to record implementation status and verified deviations

**Interfaces:**
- Consumes: All M3 components from Tasks 1–5.
- Produces: acceptance evidence for T1–T18 and a configuration invariant preventing accidental removal of recovered-offset commit behavior.

- [ ] **Step 1: Add end-to-end acceptance tests**

Implement the remaining matrix cases:

```java
// T16: independent terminal failures preserve both immutable rows
assertThat(dltRepository.findBySimulationId(simulationId))
        .extracting(SimulationDltOutboxJpaEntity::getDltEventId)
        .containsExactlyInAnyOrder(eventOne, eventTwo);

// T10: redelivery after durable recovery is idempotent
assertThat(dltRepository.findBySourceCoordinatesAndAttempt(...)).hasSize(1);
```

Include attributed/unattributed poison paths, crash-equivalent duplicate recovery, and M1/M2 non-interaction.

- [ ] **Step 2: Add configuration invariant tests**

Assert the Kafka error handler has recovered-offset commit enabled and no `DeadLetterPublishingRecoverer` bean is present in the worker context. Assert the DLT publisher is conditional on `probity.simulation.worker.enabled`.

- [ ] **Step 3: Run the complete worker verification**

Run:

```powershell
.\mvnw.cmd -pl probity-worker -am test
.\mvnw.cmd -pl probity-api -am test
```

If Testcontainers is unavailable, run the compilation and non-container selectors, report the environment limitation, and do not replace integration assertions with unverified mocks.

- [ ] **Step 4: Self-review the implementation against the design**

Check every M3 acceptance gate: atomic rollback, A/B fencing, poison termination, immutable columns, multiple events per simulation, publisher lease fencing, M1/M2 regressions, and no M4 replay behavior. Search for stale references to `DeadLetterPublishingRecoverer`, old `markFailedIfPending(UUID)`, and any DLT Kafka send outside `DltOutboxPublisher`.

- [ ] **Step 5: Update implementation status**

Change only the status/reporting portion of `docs/plans/M3_DURABLE_DLT_DESIGN.md` to identify the implemented files, tests run, and any explicitly documented deviation. Do not rewrite the approved design decisions.

- [ ] **Step 6: Commit the verification/documentation slice**

```powershell
git add probity-worker/src/test/java/me/veselin/probity/simulation/async probity-api/src/test/java/me/veselin/probity/simulation/config/SimulationWorkerConfigInvariantTest.java docs/plans/M3_DURABLE_DLT_DESIGN.md
git commit -m "test: verify durable DLT handling"
```

Include the required Copilot co-author trailer if committing.
