# M1 Acceptance Summary — Transactional Outbox Implementation (Phase 4)

## Status: CODE LEVEL VERIFIED & INTEGRATION SUITE PASSED (Commit `73031b4`)

M1 remediation is **CODE-COMPLETE AND VERIFIED** across unit and integration test suites as of commit `73031b4`. All four critical defects identified during audit and remediation have been resolved, and all 9 targeted M1 integration scenarios in `OutboxIntegrationTest` have executed and passed (9/9, 0 failures, 0 errors, 0 skipped) against Testcontainers PostgreSQL and Kafka. In addition, the full multi-module regression suite passes across all reactor modules (`probity-shared`, `probity-worker`, and `probity-api`).

> **Note on Scope**: This summary certifies code implementation, interface consistency, transactional guarantees, and automated test execution in Testcontainers. Runtime verification against external production infrastructure (live PostgreSQL role privilege checks and production cluster deployment) remains an operational deployment milestone and is not claimed here.

---

## Source Revision & Verification Environment

- **Git Commit**: `73031b4b38231033b21d0758b19eaf76ca5866ef` (`origin/master`)
- **JDK Version**: Oracle OpenJDK 21.0.9 (LTS)
- **Container Environment**: Testcontainers (PostgreSQL `postgres:16`, Kafka `apache/kafka:3.7.0`)
- **Build Tool**: Apache Maven (Maven Wrapper)

---

## Test Execution Evidence

### 1. Targeted M1 Integration Test Suite
- **Command**:
  ```powershell
  .\mvnw.cmd test -pl probity-worker -Dtest=OutboxIntegrationTest
  ```
- **Results**:
  - **Tests run**: `9`
  - **Failures**: `0`
  - **Errors**: `0`
  - **Skipped**: `0`
  - **Time elapsed**: `19.98 s`

### 2. Full Regression Suite (All Modules)
- **Command**:
  ```powershell
  .\mvnw.cmd test -pl probity-api -am
  ```
- **Module Breakdown**:
  - `probity-shared`: **SUCCESS** (unit tests passed)
  - `probity-worker`: **SUCCESS** (Tests executed: `24`, Passed: `23`, Skipped: `1` [placeholder test], Failures: `0`, Errors: `0`, Time: ~1m 58s)
  - `probity-api`: **SUCCESS** (Tests executed: `363`, Passed: `363`, Skipped: `0`, Failures: `0`, Errors: `0`, Time: ~1m 10s)
- **Total Suite Outcome**: Tests executed: `387`, Passed: `386`, Skipped: `1`, Failures: `0`, Errors: `0` (100% of non-skipped tests passed).

---

## Remediation Summary of Audited Defects

### Defect A: Compilation Mismatch in `runSimulationAsync`
- **Resolution**: Aligned `SimulationPort.runSimulationAsync(...)` and `SimulationApplicationService.runSimulationAsync(...)` to accept 9 parameters, including `Map<String, BigDecimal> marketDataSnapshot`. Updated `SimulationController` to pass `marketDataSnapshot` to the port. All modules compile without warnings or errors.

### Defect B: Duplicate Kafka Publishing
- **Resolution**: Removed `SimulationEventPublisher` and `domainEventPublisher.publish(...)` invocation from `SimulationController.java`. The transactional outbox (`simulation_outbox` table) written by `SimulationApplicationService` is now the sole authoritative API-to-Kafka handoff mechanism. Market data snapshots are durably serialized into the outbox JSONB payload.

### Defect C: Test Fixtures Seeding
- **Resolution**: Updated `BaseAsyncSimulationIntegrationTest.seedPendingSimulation()` to insert both the `Simulation` entity and the corresponding `simulation_outbox` record. Integration tests assert against genuine outbox rows created during fixture setup.

### Defect D: Test Cleanup Isolation
- **Resolution**: Updated `BaseAsyncSimulationIntegrationTest.cleanWorkspace()` to delete `simulation_outbox` records prior to deleting simulations, positions, and portfolios, eliminating state leakage across test executions.

### Defect E: Acceptance Documentation Synchronization
- **Resolution**: Updated documentation to reference verified commit `73031b4`, explicit surefire report figures, and separated code/test verification from operational production infrastructure checks.

---

## Acceptance Criteria Status Matrix

| Criterion | Level | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **Code Implementation** | Codebase | ✅ COMPLETE | Single `@Transactional` boundary for simulation + outbox write; single publisher path. |
| **Module Compilation** | Build | ✅ VERIFIED | `mvn compile` passes with zero errors on `probity-shared`, `probity-worker`, and `probity-api`. |
| **Targeted M1 Scenarios (1–9)** | Automated Tests | ✅ VERIFIED | `OutboxIntegrationTest`: 9 executed, 9 passed, 0 skipped, 0 failed against Testcontainers. |
| **Full Regression Suite** | Automated Tests | ✅ VERIFIED | 387 executed, 386 passed, 1 skipped (`probity-api` 363 + `probity-worker` 24). |
| **Idempotency (Normal Redis)** | API Filter | ✅ VERIFIED | `IdempotencyFilter` with Redis `SETNX`, 409 conflict on in-flight requests, and cached 202 replays. |
| **Idempotency Durability (Redis loss / pre-cache crash)** | Persistence | ⚠️ FUTURE (M4) | Database lacks unique `(user_id, idempotency_key)` constraint; duplicate possible if Redis loses state or node crashes before response cache. Tracked for M4. |
| **Snapshot Durability** | Code & Tests | ✅ VERIFIED | Snapshot stored in `simulation_outbox.payload` JSONB and propagated through `SimulationRequestedEvent`. |
| **Production DB Role Grants** | Infrastructure | ⚠️ OUTSTANDING | V20 migration includes conditional grants; live `has_table_privilege` verification against prod DB required at deployment. |
| **Production Worker Startup** | Infrastructure | ⚠️ OUTSTANDING | Production profile configuration verified; runtime startup against external cluster required at deployment. |

---

## Conclusion & M2 Unblocking

M1 remediation is **CLOSED** for implementation and automated testing at commit `73031b4`. M2 (Reconciliation Sweeper) is **UNBLOCKED** to proceed with implementation against this verified baseline.

---

*Last Updated: 2026-11-03 | Baseline Commit: `73031b4`*