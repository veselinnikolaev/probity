# M4.2 Dispatch-Generation Fencing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a monotonic dispatch-generation fence so stale or malformed Kafka deliveries cannot claim or mutate a newer simulation dispatch, while preserving the M1-M4.1 outbox, attempt-fencing, DLT, reconciliation, and durable-idempotency behavior.

**Architecture:** Store generation `1` on every existing simulation and simulation-outbox row, carry the same value in the event envelope and `dispatch-generation` Kafka header, and require it in every worker claim and terminal write. Resolve the message generation before deserialization when possible, classify stale/future/corrupt messages explicitly, and keep DLT history append-only while skipping simulation mutation when the message cannot be trusted. M4.3 owns generation increments and replay transactions; M4.4 owns rollout gates and fleet automation, so this plan does not add either.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA/native PostgreSQL queries, Flyway, Spring Kafka, Jackson records, Micrometer, JUnit 5, Testcontainers PostgreSQL/Kafka.

**Spec:** `docs/plans/M4_IDEMPOTENT_RETRY_DESIGN.md` — §§1.5–1.9, 4.5–4.11, 5.3–5.7, with M4.2 scope limited to fencing and compatibility surfaces.

## Global Constraints

- `dispatch_generation INTEGER NOT NULL DEFAULT 1` is incremented only by the future replay milestone; M4.2 never increments it.
- The database generation is authoritative at claim time; the header and envelope are advisory carriers, and disagreement is corruption.
- The event adds trailing nullable `Integer dispatchGeneration`; absent values are not silently coerced to `1`.
- The Kafka header is `dispatch-generation`, encoded as decimal ASCII bytes.
- A valid carrier matches `^[1-9][0-9]{0,9}$` and is `<= 2147483647`; trimming, signs, zero, leading zeroes, and arbitrary text are invalid.
- `probity.dispatch.legacy-generation-default` defaults to `false`; it applies only when both carriers are absent and never rescues malformed or conflicting carriers.
- A stale message is acknowledged, counted, and dropped without parking, retry, DLT insertion, or simulation mutation.
- A future message is acknowledged, alerted, counted, and recorded in DLT as `FUTURE`; it is never claimed or treated as stale.
- Malformed, absent-with-compatibility-disabled, and conflicting carriers fail closed; the DLT row records `NULL` parsed generation plus the raw value and simulation state is untouched.
- Attempt fencing remains in place; generation predicates are added alongside it, never substituted for it.
- Reconciliation may rotate claim/publication lease tokens but must never write `payload` or `dispatch_generation`.
- DLT rows remain append-only; no replay, DLT deletion, or DLT mutation is introduced.
- Preserve existing M4.1 migrations `V23__idempotency_record.sql` and `V24__idempotency_reconstruction_metadata.sql`; because V23/V24 are occupied, M4.2 staged migrations use `V25_1` through `V25_5`.
- Do not implement M4.3 replay/audit/idempotency business flow, M4.4 rollout automation, commits, pushes, rebases, resets, or cleanup of unrelated files.
- Do not modify the primary checkout’s unrelated untracked files `M1_M2_DISCREPANCY_INVESTIGATION.md` or `compile_output.txt`.

## Review Focus

- **Old generation after a replay-like generation bump:** it must be acknowledged before existing park/seek logic and leave status, attempt, and result unchanged; cover in the consumer integration tests.
- **Header/envelope disagreement:** it must fail closed, alert, and append one DLT row containing both raw carriers without claiming; cover in generation-resolution and consumer tests.
- **Poison payload with a usable or malformed header:** header inspection must work before deserialization, and malformed values must never become generation `1`; cover in recoverer/DLT integration tests.
- **Future generation:** it must be visible as abnormal evidence (`FUTURE`) and never be claimed or counted as stale; cover in consumer/DLT tests.
- **Reconciliation or stale publisher completion racing a generation change:** old claim tokens must update zero rows, and reconciliation must not write generation/payload; cover in outbox/reconciliation integration tests.

---

### Task 1: Add staged M4.2 generation schema

**Files:**
- Create: `probity-api/src/main/resources/db/migration/V25_1__dispatch_generation_columns.sql`
- Create: `probity-api/src/main/resources/db/migration/V25_2__dispatch_generation_indexes.sql`
- Create: `probity-api/src/main/resources/db/migration/V25_3__dispatch_generation_constraints.sql`
- Create: `probity-api/src/main/resources/db/migration/V25_4__dispatch_generation_grants.sql`
- Create: `probity-api/src/main/resources/db/migration/V25_5__dispatch_generation_validation.sql`
- Create: matching test migrations under `probity-worker/src/test/resources/db/migration/` so worker integration tests run against the same schema
- Test: migration/grant integration coverage in `probity-worker/src/test/java/me/veselin/probity/simulation/async/DispatchGenerationSchemaIntegrationTest.java`

**Interfaces:**
- Consumes: existing `simulations`, `simulation_outbox`, and `simulation_dlt_outbox` tables from V22; existing `idempotency_record` from V23/V24 remains untouched.
- Produces: `simulations.dispatch_generation`, `simulation_outbox.dispatch_generation`, nullable DLT generation metadata, the outbox payload-generation check, and indexes/constraints needed by later repository queries.

- [ ] **Step 1: Write the failing migration assertions**

  Add a Testcontainers/Flyway test that migrates a fixture containing pre-M4.2 rows, then asserts:

  ```java
  assertEquals(1, jdbc.queryForObject(
      "select dispatch_generation from simulations where id = ?",
      Integer.class, simulationId));
  assertEquals(1, jdbc.queryForObject(
      "select dispatch_generation from simulation_outbox where simulation_id = ?",
      Integer.class, simulationId));
  assertTrue(jdbc.queryForObject(
      "select convalidated from pg_constraint where conname = 'ck_outbox_payload_generation'",
      Boolean.class));
  ```

  Also assert that `simulation_dlt_outbox.dispatch_generation` is nullable and that the generation indexes exist.

- [ ] **Step 2: Run the focused test and verify it fails**

  Run:

  ```powershell
  .\mvnw.cmd -pl probity-worker -Dtest=DispatchGenerationSchemaIntegrationTest test
  ```

  Expected: FAIL because the generation columns, constraints, and indexes do not exist.

- [ ] **Step 3: Add the additive staged migrations**

  In `V25_1`, add `dispatch_generation INTEGER NOT NULL DEFAULT 1` to `simulations` and `simulation_outbox`, add nullable `dispatch_generation`, `dispatch_generation_raw VARCHAR(64)`, and `dispatch_generation_source VARCHAR(20)` to `simulation_dlt_outbox`, and add `CHECK (dispatch_generation >= 1) NOT VALID` checks.

  In `V25_2`, use `CREATE UNIQUE INDEX CONCURRENTLY` for `(id, dispatch_generation)` on `simulations` and `CREATE INDEX CONCURRENTLY` for `(simulation_id, dispatch_generation)` on the DLT table. Configure the migration as non-transactional using the repository’s existing Flyway convention.

  In `V25_3`, attach the unique/index-backed constraints, add the deferred outbox-to-simulation generation foreign key and `NOT VALID` payload check:

  ```sql
  CHECK (
      payload->>'dispatchGeneration' IS NULL
      OR (payload->>'dispatchGeneration')::int = dispatch_generation
  )
  ```

  In `V25_4`, extend only the existing least-privilege grants: the worker may insert DLT generation metadata but may not update `simulation_outbox.payload` or `dispatch_generation`; do not grant any DLT or replay delete/update privilege. Keep the API/worker role distinction and existing column-scoped publication grants intact.

  In `V25_5`, validate the generation and payload constraints and run `ANALYZE` on the affected tables. Keep the five migration names/order exact so Flyway applies columns, indexes, constraints, grants, and validation in that order.

- [ ] **Step 4: Run migration and grant tests**

  Run:

  ```powershell
  .\mvnw.cmd -pl probity-worker -Dtest=DispatchGenerationSchemaIntegrationTest test
  ```

  Expected: PASS, including generation `1` for existing rows, validated constraints, concurrent-index-backed lookups, and negative worker/API column-privilege assertions where the test database exposes both roles.

### Task 2: Propagate and resolve generation in the shared event contract

**Files:**
- Modify: `probity-shared/src/main/java/me/veselin/probity/common/domain/event/SimulationRequestedEvent.java`
- Create: `probity-shared/src/main/java/me/veselin/probity/common/domain/event/DispatchGeneration.java`
- Create: `probity-shared/src/test/java/me/veselin/probity/common/domain/event/DispatchGenerationTest.java`
- Modify: `probity-api/src/main/java/me/veselin/probity/simulation/event/SimulationEventPublisher.java`

**Interfaces:**
- Consumes: Jackson record deserialization and the generation rules in §1.9.
- Produces: `SimulationRequestedEvent(..., Integer dispatchGeneration)` and immutable `withDispatchGeneration(int)`; `DispatchGeneration.resolve(String headerValue, Integer envelopeValue, boolean legacyDefaultEnabled)` returning a typed result that distinguishes `VALID`, `ABSENT`, `MALFORMED`, and `CORRUPT`, including parsed value, raw header, and source.

- [ ] **Step 1: Write failing contract and parser tests**

  Cover valid `1`, valid current generation, missing header with envelope, both carriers absent with compatibility enabled/disabled, malformed values `"0"`, `"3x"`, `"-1"`, `"0003"`, and 11 digits, disagreement between `"2"` and `3`, and `withDispatchGeneration(2)` preserving every other event component.

  Example:

  ```java
  @Test
  void disagreeingCarriersFailClosed() {
    DispatchGeneration.Resolution result =
        DispatchGeneration.resolve("2", 3, true);
    assertEquals(DispatchGeneration.Source.CORRUPT, result.source());
    assertNull(result.value());
  }
  ```

- [ ] **Step 2: Run the focused shared tests and verify failure**

  Run:

  ```powershell
  .\mvnw.cmd -pl probity-shared -Dtest=DispatchGenerationTest test
  ```

  Expected: FAIL because the new record component and resolver do not exist.

- [ ] **Step 3: Implement the nullable trailing event field and strict resolver**

  Append `Integer dispatchGeneration` to the record so legacy JSON without the field still deserializes. Implement `withDispatchGeneration(int)` by constructing a new record with all original values unchanged. Reject null-to-one coercion in the event type itself.

  Implement strict ASCII decimal parsing with the stated regular expression and integer ceiling. Resolve header first, then envelope, then the explicit legacy flag. Return source metadata suitable for metrics and DLT persistence; do not throw for expected malformed input.

- [ ] **Step 4: Run the shared tests**

  Run the same Maven command and expect PASS, including Jackson round-trip compatibility for payloads with and without `dispatchGeneration`.

### Task 3: Add generation-aware persistence and publisher fencing

**Files:**
- Modify: `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationJpaRepository.java`
- Modify: `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationRepository.java`
- Modify: `probity-worker/src/main/java/me/veselin/probity/simulation/service/OutboxPublisher.java`
- Modify: `probity-api/src/main/java/me/veselin/probity/simulation/event/SimulationEventPublisher.java`
- Test: `probity-worker/src/test/java/me/veselin/probity/simulation/async/SimulationFencedCompleteIntegrationTest.java`
- Test: `probity-worker/src/test/java/me/veselin/probity/simulation/async/OutboxIntegrationTest.java`

**Interfaces:**
- Consumes: `DispatchGeneration`, existing attempt/fence token APIs, and V25 schema.
- Produces: `SimulationRepository.claimForProcessingWithAttempt(UUID id, int generation, Duration lease)`, generation-aware `complete`, `releaseToPending`, `markFailedForAttempt`, `markFailedIfPending`, generation-bearing outbox batch rows, and publisher sends that stamp both carriers.

- [ ] **Step 1: Add failing repository tests for cross-generation writes**

  Seed a simulation at generation `2`, then assert a generation-`1` claim returns empty and does not change status. Assert a completion/release/failure call with the correct attempt token but the wrong generation updates zero rows. Assert the current generation still succeeds with the current attempt token.

- [ ] **Step 2: Run the focused repository tests**

  Run:

  ```powershell
  .\mvnw.cmd -pl probity-worker -Dtest=SimulationFencedCompleteIntegrationTest test
  ```

  Expected: FAIL until generation is included in the repository predicates and method signatures.

- [ ] **Step 3: Add generation predicates to every conditional SQL write**

  Add `AND s.dispatchGeneration = :generation` to claim, complete, release, attempt-failure, and unclaimed-failure predicates. Keep the existing status/attempt/timestamp fences. Extend live-status lookup to return status plus generation so the consumer can classify stale versus future after a zero-row claim.

  Extend `lockPendingOutboxBatch` to return `dispatch_generation` in the same row as payload, simulation ID, and outbox ID. Do not derive generation from JSON payload.

- [ ] **Step 4: Stamp publisher output from the database row**

  Extend `ClaimedRecord` with the database generation. Before sending, call `event.withDispatchGeneration(record.dispatchGeneration())` and send a `dispatch-generation` header containing `Integer.toString(generation).getBytes(StandardCharsets.US_ASCII)`. Update the API’s direct `SimulationEventPublisher` to use generation `1` for the pre-replay event path and emit the same envelope/header pair; the future replay milestone will publish only through the generation-bearing outbox path. Keep the original payload snapshot untouched and retain claim-token fencing on publication success/failure.

- [ ] **Step 5: Run publisher and fencing tests**

  Run:

  ```powershell
  .\mvnw.cmd -pl probity-worker -Dtest=SimulationFencedCompleteIntegrationTest,OutboxIntegrationTest test
  ```

  Expected: PASS, including a test that verifies header and envelope generation match the row and a stale publisher mark affects zero rows after the row is re-leased.

### Task 4: Implement consumer classification before park/seek

**Files:**
- Modify: `probity-worker/src/main/java/me/veselin/probity/simulation/event/SimulationRequestedEventConsumer.java`
- Modify: `probity-worker/src/main/java/me/veselin/probity/simulation/config/SimulationProperties.java`
- Modify: `probity-worker/src/main/java/me/veselin/probity/simulation/reconciliation/ReconciliationMetrics.java` or add a focused metrics component following its existing naming pattern
- Modify: `probity-worker/src/main/resources/application.yaml`
- Modify: `probity-worker/src/test/resources/application-test.yaml`
- Create: `probity-worker/src/test/java/me/veselin/probity/simulation/async/DispatchGenerationConsumerIntegrationTest.java`

**Interfaces:**
- Consumes: event envelope generation, `dispatch-generation` header, `DispatchGeneration.resolve`, generation-aware repository claim, and live status/generation lookup.
- Produces: explicit `ACK_STALE`, `ACK_FUTURE`, `FAIL_CLOSED`, and normal same-generation claim/park behavior; metrics `dispatch.stale`, `dispatch.future`, `dispatch.malformed`, `dispatch.corrupt`, `dispatch.generation.header_missing`, and `dispatch.generation.legacy_default`.

- [ ] **Step 1: Write failing Kafka integration tests**

  Add tests for:

  ```java
  // row generation 2, message generation 1:
  // acknowledge, no attempt, no DLT, no park
  // row generation 2, message generation 3:
  // acknowledge, alert/metric, DLT source FUTURE, no attempt
  // same generation:
  // existing claim and execution path remains unchanged
  ```

  Include a header/envelope mismatch test and assert the simulation row remains unchanged.

- [ ] **Step 2: Run the focused consumer tests and verify failure**

  Run:

  ```powershell
  .\mvnw.cmd -pl probity-worker -Dtest=DispatchGenerationConsumerIntegrationTest test
  ```

  Expected: FAIL because the current listener claims without generation and routes all zero-row claims through park/seek.

- [ ] **Step 3: Resolve and classify before calling `handleUnclaimable`**

  Read the `dispatch-generation` header using Spring Kafka’s received-header support or a listener record-aware entry point. Resolve header/envelope through the shared resolver. For malformed/absent-disabled/corrupt results, persist the DLT evidence before acknowledging. For a valid candidate, call the generation-aware claim.

  When claim returns zero, read status plus current generation once. If current generation is greater than the message generation, acknowledge and record stale metric. If current generation is lower, acknowledge, alert, record future DLT evidence, and never park. Only equal generation falls through to existing `PROCESSING`/`PENDING`/terminal handling.

- [ ] **Step 4: Add the compatibility property and metrics**

  Add `probity.dispatch.legacy-generation-default` with environment binding `PROBITY_DISPATCH_LEGACY_GENERATION_DEFAULT` and default `false`. Preserve the existing claim lease and partition-parking settings. Add counters using the repository’s existing Micrometer naming style; do not silently downgrade an alert-worthy future/corrupt case.

- [ ] **Step 5: Run the consumer integration suite**

  Run:

  ```powershell
  .\mvnw.cmd -pl probity-worker -Dtest=DispatchGenerationConsumerIntegrationTest,SimulationAsyncDuplicateDeliveryIntegrationTest,SimulationAsyncPartitionParkIntegrationTest test
  ```

  Expected: PASS; duplicate same-generation behavior remains unchanged while stale generation bypasses parking.

### Task 5: Fence DLT and poison-message recovery

**Files:**
- Modify: `probity-worker/src/main/java/me/veselin/probity/simulation/config/SimulationFailedRecordRecoverer.java`
- Modify: `probity-worker/src/main/java/me/veselin/probity/simulation/dlt/FailureContext.java`
- Modify: `probity-worker/src/main/java/me/veselin/probity/simulation/dlt/SimulationDltEventWriter.java`
- Modify: `probity-worker/src/main/java/me/veselin/probity/simulation/dlt/WriteResult.java`
- Modify: `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationDltOutboxJpaEntity.java`
- Modify: `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationDltOutboxJpaRepository.java`
- Modify: `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationRepository.java`
- Test: `probity-worker/src/test/java/me/veselin/probity/simulation/async/SimulationAsyncUndeserializablePayloadIntegrationTest.java`
- Create: `probity-worker/src/test/java/me/veselin/probity/simulation/async/DispatchGenerationDltIntegrationTest.java`

**Interfaces:**
- Consumes: raw `ConsumerRecord` headers, `DispatchGeneration.Resolution`, generation-aware failure methods, and append-only DLT insert.
- Produces: `FailureContext` generation/source/raw metadata; write results that distinguish `RECORDED`, `RECORDED_STALE`, `RECORDED_FUTURE`, `RECORDED_MALFORMED`, and `NOT_OWNER`.

- [ ] **Step 1: Write failing poison and stale-failure tests**

  Cover a poison payload with valid header generation, malformed header, absent header with compatibility disabled, stale generation on a pending row, and future generation. Assert DLT metadata, original payload bytes, and no simulation mutation for every untrusted case.

- [ ] **Step 2: Run the focused DLT tests**

  Run:

  ```powershell
  .\mvnw.cmd -pl probity-worker -Dtest=SimulationAsyncUndeserializablePayloadIntegrationTest,DispatchGenerationDltIntegrationTest test
  ```

  Expected: FAIL because `FailureContext` has no generation and the unclaimed path still uses unfenced `markFailedIfPending`.

- [ ] **Step 3: Capture generation before deserialization**

  In `SimulationFailedRecordRecoverer.accept`, read the `dispatch-generation` header before inspecting or serializing the value. Use the deserialized envelope only when available and apply the shared resolver. Preserve raw carrier text truncated to 64 characters for DLT evidence. A valid header is sufficient for poison payloads; a malformed header produces a null parsed generation and source `MALFORMED`.

- [ ] **Step 4: Make every DLT simulation write generation-aware**

  Pass generation to `markFailedForAttempt` and replace the unclaimed `markFailedIfPending` call with a pending-plus-generation predicate. When parsed generation is null, insert the DLT row but skip simulation mutation. When stale, insert immutable DLT history but return `RECORDED_STALE` without touching the row. Keep `REQUIRES_NEW` for the recoverer transaction; do not copy it into future replay code.

- [ ] **Step 5: Run DLT and regression tests**

  Run:

  ```powershell
  .\mvnw.cmd -pl probity-worker -Dtest=SimulationAsyncUndeserializablePayloadIntegrationTest,DispatchGenerationDltIntegrationTest,SimulationAsyncPoisonPayloadProductionConfigIntegrationTest test
  ```

  Expected: PASS with byte-preserved poison payloads, generation metadata, immutable DLT rows, and no unfenced state transition.

### Task 6: Preserve reconciliation and lease-token invariants

**Files:**
- Modify: `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/SimulationJpaRepository.java`
- Modify: `probity-worker/src/main/java/me/veselin/probity/simulation/reconciliation/ReconciliationSweeper.java` only for behavior/documentation that depends on token rotation
- Modify: `probity-worker/src/test/java/me/veselin/probity/simulation/async/ReconciliationIntegrationTest.java`
- Modify: `probity-worker/src/test/java/me/veselin/probity/simulation/async/OutboxIntegrationTest.java`

**Interfaces:**
- Consumes: existing `lockOutboxForReconciliation`, reset methods, generation columns, and outbox claim-token fence.
- Produces: reconciliation reset SQL that clears/rotates `claim_token` and `claimed_until`, preserves `dispatch_generation` and `payload`, and prevents an old publisher from marking a reset row published.

- [ ] **Step 1: Write failing race-preservation tests**

  Snapshot outbox generation and payload JSONB, run a reconciliation reset, then assert both are unchanged and the claim token is cleared. Simulate an old publisher token after reset and assert `markOutboxPublished` returns zero. Run the existing stale-processing reset test and assert the simulation generation remains unchanged.

- [ ] **Step 2: Run reconciliation tests and verify failure**

  Run:

  ```powershell
  .\mvnw.cmd -pl probity-worker -Dtest=ReconciliationIntegrationTest,OutboxIntegrationTest test
  ```

  Expected: FAIL if any reset path leaves a stale token usable or accidentally updates generation/payload.

- [ ] **Step 3: Tighten reset SQL without adding generation writes**

  Keep the row lock before reset, set `claim_token = NULL` and `claimed_until = NULL` on reset, and leave `dispatch_generation` and `payload` absent from every sweeper update. Preserve existing cooldown, observed-state, and alert behavior.

- [ ] **Step 4: Run the focused and full worker regression tests**

  Run:

  ```powershell
  .\mvnw.cmd -pl probity-worker test
  ```

  Expected: PASS, including existing M1-M4.1 tests for outbox publication, attempt fencing, DLT publication, and durable idempotency compatibility.

### Task 7: Complete M4.2 review and documentation checks

**Files:**
- Modify: `docs/operations.md` with the generation-carrier, stale/future/malformed actions, and the compatibility flag’s removal precondition
- Modify: `probity-worker/src/main/resources/application.yaml` and test configuration only for documented M4.2 properties
- Test: all focused tests from Tasks 1–6

**Interfaces:**
- Consumes: implementation behavior and §5.10 deployment assumptions.
- Produces: operator-visible documentation that explains generation `1`, stale-message no-op semantics, DLT evidence, and the fact that replay/rollout remain future milestones.

- [ ] **Step 1: Add the operator behavior documentation**

  Document that `dispatch_generation` is initialized to `1`, stale messages are normal after a future replay and are not DLT failures, future generations indicate database/producer divergence, malformed carriers fail closed, and the legacy default flag must remain disabled unless an explicitly managed compatibility window enables it. Do not document an M4.2 replay endpoint because it is out of scope.

- [ ] **Step 2: Run the exact review-focus commands**

  Run:

  ```powershell
  .\mvnw.cmd -pl probity-shared -Dtest=DispatchGenerationTest test
  .\mvnw.cmd -pl probity-worker -Dtest=DispatchGenerationSchemaIntegrationTest,SimulationFencedCompleteIntegrationTest,OutboxIntegrationTest,DispatchGenerationConsumerIntegrationTest,DispatchGenerationDltIntegrationTest,ReconciliationIntegrationTest test
  ```

  Expected: PASS with no product changes outside the mapped files.

- [ ] **Step 3: Perform the plan-to-spec self-review before implementation**

  Check every M4.2 requirement in `M4_IDEMPOTENT_RETRY_DESIGN.md` §§1.5–1.9 and 5.3–5.7 against a task above; scan this plan for `TODO`, `TBD`, “similar to,” or unspecified error-handling placeholders; verify all generation method signatures match across Tasks 2–5; and confirm each Review Focus line has a concrete test owner. Any gap must be fixed in this plan before implementation begins.
