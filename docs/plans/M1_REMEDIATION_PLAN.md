# M1 Remediation Plan — Transactional Outbox Implementation

**Status**: M1 is BLOCKED for M2. Critical defects prevent compilation and correct operation.

**Date**: 2026-11-03

**Decision**: M2 is BLOCKED until M1 is independently remediated and verified.

---

## 1. Verified Git and Remote State

### Current State
- **Branch**: `master`
- **HEAD**: `973df3f1b9969bece0dc89977e23e05e698fa552`
- **origin/master**: `973df3f1b9969bece0dc89977e23e05e698fa552`
- **Status**: Clean working tree (no uncommitted changes affecting M1)
- **Previous reported commit**: `9786f10` (appears to be a merge parent, not the current HEAD)

### Conclusion
The current HEAD matches origin/master exactly. There are no uncommitted changes that would affect M1 behavior. The discrepancy between the previously reported `9786f10` and current `973df3f` is explained by the merge commit at HEAD which has `9786f10` as its first parent.

---

## 2. Defect-by-Defect Evidence

### Defect A: Compilation Mismatch in `runSimulationAsync`

**Classification**: CONFIRMED DEFECT

**Evidence**:
- **File**: `probity-worker/src/main/java/me/veselin/probity/simulation/port/SimulationPort.java:22`
  ```java
  SimulationStatusResponse runSimulationAsync(UUID portfolioId, int numberOfSimulations, int timeHorizonDays, 
      double confidenceLevel, Double assumedReturnPercent, Double assumedVolatilityPercent, UUID userId, String idempotencyKey);
  ```
  **8 parameters** (no `marketDataSnapshot`)

- **File**: `probity-worker/src/main/java/me/veselin/probity/simulation/service/SimulationApplicationService.java:78`
  ```java
  public SimulationStatusResponse runSimulationAsync(UUID portfolioId, int numberOfSimulations, int timeHorizonDays, 
      double confidenceLevel, Double assumedReturnPercent, Double assumedVolatilityPercent, UUID userId, 
      String idempotencyKey, Map<String, BigDecimal> marketDataSnapshot)
  ```
  **9 parameters** (adds `marketDataSnapshot`)

- **File**: `probity-api/src/main/java/me/veselin/probity/bff/controller/SimulationController.java:137-146`
  ```java
  SimulationStatusResponse statusResponse = simulationPort.runSimulationAsync(
      request.portfolioId(),
      request.numberOfSimulations(),
      request.timeHorizonDays(),
      request.confidenceLevel(),
      request.assumedReturnPercent(),
      request.assumedVolatilityPercent(),
      principal.id(),
      idempotencyKey
  );
  ```
  Calls with **8 arguments**, not passing `marketDataSnapshot`

**Root Cause**: The interface and implementation signatures disagree. The implementation added `marketDataSnapshot` parameter but the interface was not updated.

**Impact**: 
- `probity-worker` module does not compile (javac error: "SimulationApplicationService is not abstract and does not override abstract method runSimulationAsync(...)")
- Even if compilation were forced, the controller would never pass the market data snapshot to the service
- The market data snapshot captured at line 135 is discarded

**Affected Components**:
- `SimulationPort.java` (interface)
- `SimulationApplicationService.java` (implementation)
- `SimulationController.java` (caller)
- Any mocks or tests that reference this method signature

---

### Defect B: Duplicate Kafka Publishing Paths

**Classification**: CONFIRMED DEFECT

**Evidence**:

**Path 1: Transactional Outbox (intended)**
- `SimulationController.java:137-146` calls `simulationPort.runSimulationAsync(...)`
- `SimulationApplicationService.java:78-134` inserts simulation row AND outbox row in same `@Transactional` method
- Outbox row is picked up by `OutboxPublisher` and published to Kafka

**Path 2: Direct Fire-and-Forget (unintended)**
- `SimulationController.java:135` captures `marketDataSnapshot`
- `SimulationController.java:151` calls `publishSimulationRequestedEvent(...)`
- `SimulationController.java:335-353` builds `SimulationRequestedEvent` and calls `domainEventPublisher.publish(event)`
- `SimulationEventPublisher.java:31-44` publishes directly to Kafka via `kafkaTemplate.send()` with only `whenComplete` logging
- No transactional guarantee, no fencing, no retry logic

**Root Cause**: The controller was not updated to remove the direct publishing path when the transactional outbox was added.

**Impact**:
- **Two Kafka publishes per request**: One via outbox (correct), one via direct fire-and-forget (incorrect)
- **Lost durability guarantee**: The direct publish has no transactional association with the simulation row
- **Duplicate delivery**: Workers may receive the same event twice (once from each path)
- **Inconsistent snapshots**: The outbox path includes the snapshot, the direct path includes the snapshot, but they're separate events

**Affected Components**:
- `SimulationController.java` (lines 135, 151, 335-353)
- `SimulationEventPublisher.java` (entire file - should not be used for SimulationRequestedEvent)
- `DomainEventPublisher` interface and implementations

**Required Fix**: Remove the direct publishing path entirely. The transactional outbox is the single authoritative API-to-Kafka handoff mechanism.

---

### Defect C: Integration Test Fixtures Never Create Outbox Rows

**Classification**: CONFIRMED DEFECT

**Evidence**:

**File**: `probity-worker/src/test/java/me/veselin/probity/simulation/async/BaseAsyncSimulationIntegrationTest.java:265-281`
```java
protected UUID seedPendingSimulation() {
    UUID pid = UUID.fromString(portfolioId);

    Simulation pending = Simulation.builder()
            .portfolioId(pid)
            .userId(SEED_USER_UUID)
            .numberOfSimulations(100)
            .timeHorizonDays(10)
            .confidenceLevel(0.95)
            .currentPortfolioValue(BigDecimal.ZERO)
            .resultPayload(null)
            .status(SimulationStatus.PENDING)
            .build();

    return simulationRepository.save(pending).getId();
}
```
**Only inserts the simulation row. Never inserts into `simulation_outbox`.**

**File**: `probity-worker/src/test/java/me/veselin/probity/simulation/async/OutboxIntegrationTest.java`
- Test 1 (lines 103-113): Asserts `outboxState(testSimulationId)` is not null
- Test 3 (lines 153-168): Asserts outbox row exists and can be published
- Test 4 (lines 173-195): Asserts outbox row exists and can fail
- Test 5 (lines 200-222): Asserts outbox row survives restart
- Test 6 (lines 227-253): Asserts outbox row can be re-published
- Test 8 (lines 279-330): Calls `seedPendingSimulation()` twice, expects two outbox rows
- Test 9 (lines 335-365): Calls `seedPendingSimulation()`, expects outbox row to exist

**Only Test 2** (lines 118-148) manually inserts an outbox row to test rollback.

**Root Cause**: The fixture was written before the outbox was added and never updated.

**Impact**:
- Tests 1, 3, 4, 5, 6, 8, 9 **cannot pass** as written
- They assert on outbox rows that don't exist
- The 9/9 claim in `M1_ACCEPTANCE_SUMMARY.md` is impossible

**Affected Components**:
- `BaseAsyncSimulationIntegrationTest.seedPendingSimulation()`
- `OutboxIntegrationTest` (all tests except #2)

**Required Fix**: Update `seedPendingSimulation()` to insert both the simulation row AND the corresponding outbox row in the same transaction, matching the production code path.

---

### Defect D: Integration Test Cleanup Leaks Outbox Rows

**Classification**: CONFIRMED DEFECT

**Evidence**:

**File**: `probity-worker/src/test/java/me/veselin/probity/simulation/async/BaseAsyncSimulationIntegrationTest.java:224-232`
```java
@AfterEach
void cleanWorkspace() {
    // Simulations first (FK to portfolios/users), then positions -> portfolios -> user.
    // Asset rows are V7 seed data and are deliberately not deleted.
    simulationRepository.deleteAll();
    jdbcTemplate.update("DELETE FROM portfolio_positions WHERE portfolio_id = ?", PORTFOLIO_UUID);
    jdbcTemplate.update("DELETE FROM portfolios WHERE id = ?", PORTFOLIO_UUID);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", SEED_USER_UUID);
}
```
**Deletes simulations, positions, portfolios, users. Does NOT delete `simulation_outbox`.**

**File**: `probity-api/src/main/resources/db/migration/V20__simulation_outbox.sql:29-42`
```sql
CREATE TABLE IF NOT EXISTS simulation_outbox (
    id UUID NOT NULL,
    simulation_id UUID NOT NULL,
    ...
    CONSTRAINT pk_simulation_outbox PRIMARY KEY (id),
    CONSTRAINT uq_simulation_id UNIQUE (simulation_id)
);
```
**No foreign key to `simulations(id)`** (intentional, per V19 precedent for load-test reset).

**Impact**:
- Outbox rows survive between tests
- `lockPendingOutboxBatch(1)` may return stale rows from previous tests
- Test execution order matters
- Tests that expect exactly one pending row will fail if previous tests left rows

**Affected Components**:
- `BaseAsyncSimulationIntegrationTest.cleanWorkspace()`
- All tests that rely on clean state

**Required Fix**: Add `jdbcTemplate.update("DELETE FROM simulation_outbox")` to `cleanWorkspace()` before deleting simulations.

---

### Defect E: Acceptance Test Validity Claim is False

**Classification**: CONFIRMED DEFECT

**Evidence**:

**File**: `M1_ACCEPTANCE_SUMMARY.md:5`
> "M1 is **FIXED AND VERIFIED** at the code level. All four critical bugs identified by the adversarial review have been fixed, and 9 integration tests have been written and executed against real PostgreSQL and Kafka via Testcontainers, all passing (9/9, 0 failures, 0 errors)."

**File**: `M1_ACCEPTANCE_SUMMARY.md:156`
> "Integration tests written and executed for all 9 scenarios | ✅ COMPLETED | OutboxIntegrationTest.java executed against Testcontainers PostgreSQL + Kafka: 9/9 pass, 0 failures, 0 errors"

**Reality**:
- The tests as written **cannot compile** due to Defect A (signature mismatch)
- Even if compiled, tests 1, 3, 4, 5, 6, 8, 9 **cannot pass** due to Defect C (missing outbox rows)
- Test order dependency due to Defect D (cleanup leak)
- No evidence provided of actual test execution (no CI logs, no local run output)
- The document was generated on "Mon Nov 02 2026" but provides no reproducible evidence

**Root Cause**: The acceptance summary was written without actually running the tests against the current codebase.

**Impact**:
- False confidence in M1 completion
- M2 design proceeded based on incorrect assumptions
- No actual verification that the transactional outbox works end-to-end

**Required Fix**: Re-run all 9 tests after fixing defects A-D, against the exact remediation commit, with reproducible evidence (CI logs or local output).

---

## 3. Additional M1 Risks Identified

### Risk 1: Idempotency Key Accepted But Never Used

**Evidence**:
- `SimulationPort.runSimulationAsync(...)` accepts `idempotencyKey` parameter
- `SimulationApplicationService.runSimulationAsync(...)` accepts `idempotencyKey` parameter
- Neither implementation actually uses the parameter
- `IdempotencyFilter` in the API layer exists but the service layer ignores the key

**Impact**:
- Idempotency is not actually enforced at the service layer
- Duplicate requests with the same key could create duplicate simulations
- The idempotency contract is violated

**Classification**: Potential defect requiring investigation

---

### Risk 2: Market Data Snapshot Not Persisted in Simulation Table

**Evidence**:
- `SimulationController.java:135` captures `marketDataSnapshot`
- `SimulationApplicationService.java:78` accepts `marketDataSnapshot` and includes it in the outbox payload
- The `simulations` table has no column for `marketDataSnapshot`
- The snapshot exists only in the outbox payload, not in the simulation row

**Impact**:
- If the outbox row is lost (before publication), the snapshot is lost forever
- No way to reconstruct what prices were intended for a given simulation
- Debugging and audit trail compromised

**Classification**: Potential defect requiring investigation

---

### Risk 3: No Foreign Key Between Outbox and Simulations

**Evidence**:
- V20 migration deliberately omits FK from `simulation_outbox.simulation_id` to `simulations.id`
- Rationale: load-test reset needs to `TRUNCATE TABLE simulations` without cascading

**Impact**:
- Orphaned outbox rows possible if simulation is deleted
- No database-level referential integrity
- Manual cleanup required for orphaned rows

**Classification**: Design trade-off (acceptable if documented and monitored)

---

### Risk 4: Scheduler Activation Not Verified in Production

**Evidence**:
- `WorkerApplication.java:58` has `@EnableScheduling`
- `OutboxPublisher.java:87-88` has `@Scheduled` annotation
- No evidence that the scheduled task actually fires in production profile
- Test profile sets `poll-interval-ms: 3600000` (effectively off)

**Impact**:
- Publisher may never run in production
- Accepted simulations would stay PENDING forever
- No alerting if scheduler is disabled

**Classification**: Potential defect requiring production verification

---

### Risk 5: Database Grants Not Runtime-Verified

**Evidence**:
- V20 migration grants SELECT/UPDATE to `probity_worker` and SELECT/INSERT to `probity_api`
- No runtime check that these grants actually exist
- No evidence of `has_table_privilege` verification against live database

**Impact**:
- Worker may fail to claim/publish outbox rows if grants are missing
- API may fail to insert outbox rows if grants are missing
- Errors would only appear at runtime

**Classification**: Potential defect requiring production verification

---

### Risk 6: Kafka Configuration Not Production-Verified

**Evidence**:
- `OutboxPublisher.java:47` uses `@Qualifier("simulationKafkaTemplate")`
- `KafkaConsumerConfig.java` creates the producer bean
- No evidence that this configuration actually works against production Kafka
- Test uses Testcontainers Kafka, not production cluster

**Impact**:
- Publisher may fail to connect to production Kafka
- Serialization errors possible in production
- No verification of producer properties (acks, retries, etc.)

**Classification**: Potential defect requiring production verification

---

## 4. Minimal Proposed Code Changes

### Change 1: Fix Signature Mismatch (Defect A)

**File**: `probity-worker/src/main/java/me/veselin/probity/simulation/port/SimulationPort.java:22`

**Before**:
```java
SimulationStatusResponse runSimulationAsync(UUID portfolioId, int numberOfSimulations, int timeHorizonDays, double confidenceLevel, Double assumedReturnPercent, Double assumedVolatilityPercent, UUID userId, String idempotencyKey);
```

**After**:
```java
SimulationStatusResponse runSimulationAsync(UUID portfolioId, int numberOfSimulations, int timeHorizonDays, double confidenceLevel, Double assumedReturnPercent, Double assumedVolatilityPercent, UUID userId, String idempotencyKey, Map<String, BigDecimal> marketDataSnapshot);
```

**File**: `probity-api/src/main/java/me/veselin/probity/bff/controller/SimulationController.java:137-146`

**Before**:
```java
SimulationStatusResponse statusResponse = simulationPort.runSimulationAsync(
    request.portfolioId(),
    request.numberOfSimulations(),
    request.timeHorizonDays(),
    request.confidenceLevel(),
    request.assumedReturnPercent(),
    request.assumedVolatilityPercent(),
    principal.id(),
    idempotencyKey
);
```

**After**:
```java
SimulationStatusResponse statusResponse = simulationPort.runSimulationAsync(
    request.portfolioId(),
    request.numberOfSimulations(),
    request.timeHorizonDays(),
    request.confidenceLevel(),
    request.assumedReturnPercent(),
    request.assumedVolatilityPercent(),
    principal.id(),
    idempotencyKey,
    marketDataSnapshot
);
```

---

### Change 2: Remove Direct Kafka Publishing (Defect B)

**File**: `probity-api/src/main/java/me/veselin/probity/bff/controller/SimulationController.java`

**Remove lines 148-151**:
```java
// Publish Kafka event with market data snapshot
// The event is published by the application service after creating the PENDING record
// We need to publish the event with the market data snapshot
publishSimulationRequestedEvent(statusResponse.getId(), request, principal.id(), marketDataSnapshot, idempotencyKey);
```

**Remove method `publishSimulationRequestedEvent` (lines 331-353)** entirely.

**Rationale**: The transactional outbox is now the single authoritative API-to-Kafka handoff. The direct fire-and-forget path is redundant and breaks durability guarantees.

---

### Change 3: Update Test Fixture to Create Outbox Row (Defect C)

**File**: `probity-worker/src/test/java/me/veselin/probity/simulation/async/BaseAsyncSimulationIntegrationTest.java:265-281`

**Before**:
```java
protected UUID seedPendingSimulation() {
    UUID pid = UUID.fromString(portfolioId);

    Simulation pending = Simulation.builder()
            .portfolioId(pid)
            .userId(SEED_USER_UUID)
            .numberOfSimulations(100)
            .timeHorizonDays(10)
            .confidenceLevel(0.95)
            .currentPortfolioValue(BigDecimal.ZERO)
            .resultPayload(null)
            .status(SimulationStatus.PENDING)
            .build();

    return simulationRepository.save(pending).getId();
}
```

**After**:
```java
protected UUID seedPendingSimulation() {
    UUID pid = UUID.fromString(portfolioId);
    UUID simulationId = UUID.randomUUID();

    Simulation pending = Simulation.builder()
            .id(simulationId)
            .portfolioId(pid)
            .userId(SEED_USER_UUID)
            .numberOfSimulations(100)
            .timeHorizonDays(10)
            .confidenceLevel(0.95)
            .currentPortfolioValue(BigDecimal.ZERO)
            .resultPayload(null)
            .status(SimulationStatus.PENDING)
            .build();

    Simulation saved = simulationRepository.save(pending);

    // Insert corresponding outbox row in the same transaction as production does
    SimulationRequestedEvent event = validEventFor(simulationId);
    String jsonPayload;
    try {
        jsonPayload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(event);
    } catch (Exception e) {
        throw new IllegalStateException("Failed to serialize simulation event", e);
    }

    simulationRepository.insertOutboxEvent(
            simulationId,
            simulationId,
            "SimulationRequestedEvent",
            jsonPayload
    );

    return saved.getId();
}
```

---

### Change 4: Add Outbox Cleanup to Test Fixture (Defect D)

**File**: `probity-worker/src/test/java/me/veselin/probity/simulation/async/BaseAsyncSimulationIntegrationTest.java:224-232`

**Before**:
```java
@AfterEach
void cleanWorkspace() {
    // Simulations first (FK to portfolios/users), then positions -> portfolios -> user.
    // Asset rows are V7 seed data and are deliberately not deleted.
    simulationRepository.deleteAll();
    jdbcTemplate.update("DELETE FROM portfolio_positions WHERE portfolio_id = ?", PORTFOLIO_UUID);
    jdbcTemplate.update("DELETE FROM portfolios WHERE id = ?", PORTFOLIO_UUID);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", SEED_USER_UUID);
}
```

**After**:
```java
@AfterEach
void cleanWorkspace() {
    // Outbox first (no FK to simulations), then simulations, then positions -> portfolios -> user.
    // Asset rows are V7 seed data and are deliberately not deleted.
    jdbcTemplate.update("DELETE FROM simulation_outbox");
    simulationRepository.deleteAll();
    jdbcTemplate.update("DELETE FROM portfolio_positions WHERE portfolio_id = ?", PORTFOLIO_UUID);
    jdbcTemplate.update("DELETE FROM portfolios WHERE id = ?", PORTFOLIO_UUID);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", SEED_USER_UUID);
}
```

---

## 5. Required Integration Test Corrections

### Test 1: Atomic Simulation/Outbox Commit
- **Status**: Will pass after Change 3 (fixture creates outbox row)
- **No additional changes needed**

### Test 2: Rollback When Outbox Insert Fails
- **Status**: Already correct (manually inserts outbox row to test rollback)
- **No changes needed**

### Test 3: Successful Publication
- **Status**: Will pass after Change 3 (fixture creates outbox row)
- **No additional changes needed**

### Test 4: Kafka Unavailable During Publication
- **Status**: Will pass after Change 3 (fixture creates outbox row)
- **No additional changes needed**

### Test 5: Recovery After Publisher Restart
- **Status**: Will pass after Change 3 (fixture creates outbox row)
- **No additional changes needed**

### Test 6: Kafka Ack + Crash Before Published At Update
- **Status**: Will pass after Change 3 (fixture creates outbox row)
- **No additional changes needed**

### Test 7: Duplicate Delivery Idempotent Guard
- **Status**: Tests consumer-side fencing, not outbox
- **No changes needed**

### Test 8: Concurrent Publishers Safety
- **Status**: Will pass after Change 3 (fixture creates outbox row)
- **No additional changes needed**

### Test 9: Expired Claim Recovery
- **Status**: Will pass after Change 3 (fixture creates outbox row)
- **No additional changes needed**

---

## 6. Regression Risks

### Risk 1: API Module May Have Calls to Old Signature
- **Mitigation**: Search entire codebase for calls to `runSimulationAsync` and update all callers
- **Verification**: Compile all modules after change

### Risk 2: Removing Direct Publishing May Break Other Consumers
- **Mitigation**: Verify no other components depend on the direct fire-and-forget path
- **Verification**: Search for usages of `SimulationEventPublisher` and `DomainEventPublisher`

### Risk 3: Test Fixture Changes May Affect Other Test Classes
- **Mitigation**: Run all integration tests, not just `OutboxIntegrationTest`
- **Verification**: Full test suite execution

### Risk 4: Outbox Cleanup May Break Tests That Depend on Leaked Rows
- **Mitigation**: Review all async simulation tests for implicit dependencies on test order
- **Verification**: Run tests in random order

---

## 7. Ordered Implementation Steps

### Step 1: Fix Signature Mismatch (Defect A)
1. Update `SimulationPort.runSimulationAsync` signature to include `marketDataSnapshot`
2. Update `SimulationController.runAsync` to pass `marketDataSnapshot` to service
3. Compile `probity-worker` module: `./mvnw.cmd -pl probity-worker -am -DskipTests compile`
4. Verify compilation succeeds

### Step 2: Remove Direct Kafka Publishing (Defect B)
1. Remove call to `publishSimulationRequestedEvent` from `SimulationController.runAsync`
2. Remove `publishSimulationRequestedEvent` method from `SimulationController`
3. Verify no other code depends on `SimulationEventPublisher` for `SimulationRequestedEvent`
4. Compile `probity-api` module: `./mvnw.cmd -pl probity-api -am -DskipTests compile`

### Step 3: Update Test Fixture (Defect C)
1. Modify `BaseAsyncSimulationIntegrationTest.seedPendingSimulation()` to insert outbox row
2. Use the same logic as production: insert simulation, then insert outbox in same transaction
3. Verify fixture creates both rows correctly

### Step 4: Add Outbox Cleanup (Defect D)
1. Modify `BaseAsyncSimulationIntegrationTest.cleanWorkspace()` to delete `simulation_outbox` first
2. Verify cleanup leaves no residual rows

### Step 5: Run Integration Tests
1. Run `OutboxIntegrationTest`: `./mvnw.cmd -pl probity-worker test -Dtest=OutboxIntegrationTest`
2. Verify all 9 tests pass
3. Run all async simulation tests: `./mvnw.cmd -pl probity-worker test -Dtest=*Async*IntegrationTest`
4. Verify no regressions

### Step 6: Full Module Compilation
1. Compile all modules: `./mvnw.cmd clean compile -DskipTests`
2. Verify no compilation errors

### Step 7: Update Documentation
1. Correct `M1_ACCEPTANCE_SUMMARY.md` to reflect actual test results
2. Remove false "9/9 executed" claim
3. Add evidence of actual test execution (CI logs or local output)

### Step 8: Commit Remediation
1. Commit changes with clear message: "Remediate M1 defects: fix signature mismatch, remove duplicate Kafka path, fix test fixtures"
2. Do NOT push until all tests pass

---

## 8. Acceptance Criteria

The remediation is complete ONLY when ALL of the following are satisfied:

### Code-Level Criteria
- [ ] All three modules (`probity-api`, `probity-worker`, `probity-shared`) compile without errors
- [ ] `SimulationPort` and `SimulationApplicationService` signatures agree
- [ ] Controller passes `marketDataSnapshot` to service
- [ ] Controller does NOT call `publishSimulationRequestedEvent`
- [ ] `SimulationEventPublisher` is not used for `SimulationRequestedEvent` in production code
- [ ] The API-to-Kafka flow uses the transactional outbox exclusively

### Test-Level Criteria
- [ ] `seedPendingSimulation()` inserts both simulation and outbox row
- [ ] `cleanWorkspace()` deletes `simulation_outbox` before `simulations`
- [ ] All 9 `OutboxIntegrationTest` scenarios pass
- [ ] Tests run against Testcontainers PostgreSQL and Kafka
- [ ] Tests pass in random order (no test order dependency)
- [ ] Full async simulation test suite passes (no regressions)

### Transactional Outbox Criteria
- [ ] Simulation and outbox insertion are atomic (same `@Transactional` method)
- [ ] Outbox row exists immediately after `runSimulationAsync` returns
- [ ] Outbox publisher claims rows with `FOR UPDATE SKIP LOCKED`
- [ ] Fencing token prevents stale publisher overwrites
- [ ] Failed publications leave rows pending for retry

### Documentation Criteria
- [ ] `M1_ACCEPTANCE_SUMMARY.md` is corrected
- [ ] False "9/9 executed" claim is removed or replaced with actual evidence
- [ ] Test execution evidence is provided (CI logs or local output)
- [ ] No claim of production verification without actual production checks

### Git Criteria
- [ ] All changes committed to a single remediation commit
- [ ] Commit message clearly describes the fixes
- [ ] No uncommitted changes remain
- [ ] HEAD matches the remediation commit exactly

---

## 9. Explicit Distinction: Code vs Production Verification

### Code Verification (This Plan)
- **Scope**: Compilation, unit tests, integration tests against Testcontainers
- **Infrastructure**: Testcontainers PostgreSQL, Testcontainers Kafka
- **Database**: Migrated by Flyway in test context
- **Kafka**: Testcontainers broker (single-node, no security)
- **What this proves**: The code logic is correct, the transactional outbox pattern is implemented correctly, tests pass against throwaway infrastructure

### Production Verification (NOT in Scope for This Plan)
- **Scope**: Runtime startup against production infrastructure, actual database grants, actual Kafka connectivity
- **Infrastructure**: Production PostgreSQL cluster, production Kafka cluster
- **Database**: Production database with production roles (`probity_api`, `probity_worker`)
- **Kafka**: Production cluster with security, ACLs, monitoring
- **What this proves**: The application actually runs in production, database grants are correct, Kafka configuration works, scheduler fires, publisher publishes

**Critical**: Do not claim production verification until the following are performed:
- Worker application starts against a migrated production database
- `has_table_privilege` checks confirm `probity_worker` can SELECT/UPDATE `simulation_outbox`
- `has_table_privilege` checks confirm `probity_api` can SELECT/INSERT `simulation_outbox`
- Kafka producer successfully connects to production cluster
- Scheduled task fires and publishes events
- End-to-end request flow works: HTTP → API → DB → Outbox → Publisher → Kafka → Worker

---

## 10. Summary

### Confirmed Defects (5)
1. **Defect A**: Compilation mismatch - interface and implementation signatures disagree
2. **Defect B**: Duplicate Kafka publishing - controller uses both outbox and direct fire-and-forget
3. **Defect C**: Test fixtures don't create outbox rows - 7 of 9 tests cannot pass
4. **Defect D**: Test cleanup leaks outbox rows - no FK, manual cleanup missing
5. **Defect E**: False acceptance claim - 9/9 never actually executed

### Potential Defects Requiring Investigation (3)
1. **Risk 1**: Idempotency key accepted but never used
2. **Risk 2**: Market data snapshot not persisted in simulation table
3. **Risk 4**: Scheduler activation not verified in production

### Design Trade-offs (1)
1. **Risk 3**: No FK between outbox and simulations (acceptable if documented)

### Production Verification Required (2)
1. **Risk 5**: Database grants not runtime-verified
2. **Risk 6**: Kafka configuration not production-verified

### Conclusion
M1 is **NOT complete**. The transactional outbox implementation has critical defects that prevent compilation and correct operation. The acceptance summary's claims are false. M2 must remain BLOCKED until M1 is remediated according to this plan and all acceptance criteria are satisfied.

**Do not proceed with M2 until this remediation is complete and verified.**
