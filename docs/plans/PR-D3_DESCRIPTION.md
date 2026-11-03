# PR-D3 — gate the simulation listener out of the API (Phase 4)

> Draft PR description. Branch: `pr-d3-disable-listener`. Gitignored/untracked — not committed.

## Scope

PR-D3 makes one behavioral guarantee: **a running api never joins the
`probity-simulation-worker` consumer group**, with no environment variable involved,
per `docs/plans/PHASE_4_WORKER_EXTRACTION.md` §2.3 (feature gate) and §2.7 (test
decision). It closes the PR-D2 *Finding*: because `probity-api` depends on
`probity-worker` for its producer, the worker's `@KafkaListener`
(`SimulationRequestedEventConsumer`) is on the api classpath and its component scan
registered it, so **every** api replica was a second consumer in the group and took
partitions from the worker. This is a correctness fix, not hygiene.

It is a gate, not a behavior change: concurrency stays at **1**, every
partitions / lease / poll / park / seek value is untouched, and the `@Disabled`
concurrency-placeholder test stays disabled (§2.7 is still a later-PR TODO).

`KafkaConsumerConfig` is **not** gated. The api publishes through the
`@Primary KafkaTemplate<String,DomainEvent>` and the `kafkaListenerContainerFactory`
defined there (`@EnableKafka`); gating that class would break the api's producer path.
Only the listener **bean** is gated.

## What it does

### The gate (one annotation)

`probity-worker/src/main/java/me/veselin/probity/simulation/event/SimulationRequestedEventConsumer.java`

```java
@ConditionalOnProperty(name = "probity.simulation.worker.enabled", matchIfMissing = true)
```

- **api** (`probity-api/src/main/resources/application.yaml`): `probity.simulation.worker.enabled: false`
- **worker** (`probity-worker/src/main/resources/application.yaml`): `probity.simulation.worker.enabled: true`

Both values are **plain literals, with no `${...}` env placeholder**. That is
deliberate: a shared environment variable (the obvious alternative) could be set once
and silently turn the worker's consumption **off** — a far worse failure than an api
that wrongly consumes. Fixed literals make the api's "off" and the worker's "on"
unconditional in each deployment.

`matchIfMissing = true` means the gate can only ever turn the listener **off** when a
context explicitly sets the flag `false`. Any slice that does not set it (for example
the worker's own tests) keeps the listener registered exactly as before.

## Existing test touched (one)

`probity-api/src/test/java/me/veselin/probity/simulation/async/SimulationRunAsyncRequestIntegrationTest.java`

- Adds `registry.add("probity.simulation.worker.enabled", () -> true);` to its
  `@DynamicPropertySource`, plus a six-line comment. **No assertion changed.**
- Why: this request-contract test asserts on the listener container's *presence but
  dormancy* (it calls `registerDormantListener`, i.e. `auto-startup=false`). With the
  api flag now `false` the bean would be absent and that assertion would no longer
  describe a reachable state, so the test re-enables the flag for its own context only.
  The api-has-no-listener invariant is asserted separately (below).

This was the **preferred** option from the §2.7 decision (add the property to the
test's own properties rather than edit its assertion). No other api test depended on
the api listener: the whole `probity-api` module (350/0) is green with the gate on.

## New tests (2)

| Module | Test | Asserts |
| --- | --- | --- |
| api | `simulation/event/SimulationListenerAbsentInApiIntegrationTest` | `KafkaListenerEndpointRegistry.getListenerContainer("simulation-requested")` is **null**, the id is absent from `getListenerContainerIds()`, and `getBeanNamesForType(SimulationRequestedEventConsumer.class).length == 0`. |
| worker | `simulation/async/SimulationListenerRegisteredInWorkerIntegrationTest` | `getListenerContainer("simulation-requested")` is **non-null** (the worker still registers it). |

Both assert on the **runtime registry / bean graph**, never by matching a log string.

**Sensitivity (each new test must fail if the gate is removed).** Verified locally by
temporarily editing and then restoring; neither edit is committed:

- remove `@ConditionalOnProperty` → api test **red** (`registered ids=[simulation-requested]`); restored.
- gate present, worker flag forced `false` → worker test **red** (`registered ids=[]`, `expected: not <null>`); restored.

`getListenerContainer(String)` returns `null` for an absent id (spring-kafka 3.3.13:
it only asserts a non-empty id, then `map.get`), so the api assertion is safe.

## Verification

### Build (final tree)

`mvnw -o clean verify` → **exit 0, BUILD SUCCESS, 164 s** (`Total time: 02:44 min`).

| Module | Tests run | Skipped |
| --- | --- | --- |
| `probity-worker` | 15 | 1 (the `@Disabled` placeholder) |
| `probity-api` | 350 | 0 |
| **total** | **365** | **1** |

Baseline (PR-D2) was **363 run / 1 skipped** → **+2 run**, exactly the two new tests;
skipped unchanged.

### Images

- `probity-api:pr-d3` — **526 MB**, builds clean.
- `probity-worker:pr-d3` — **442 MB**, builds clean.

### Runtime — one consumer, no env var

Both apps started as `java -jar` with **no listener-related environment variable**
(no `SPRING_KAFKA_LISTENER_AUTO_STARTUP`):

```
GROUP                     CONSUMER-ID                                       HOST        #PARTITIONS
probity-simulation-worker consumer-probity-simulation-worker-1-77c3e581-... /172.19.0.1 6
```

`kafka-consumer-groups --describe --group probity-simulation-worker` shows that single
member holding partitions 0–5 with `LAG=0` on each. One `run-async` reached
`COMPLETED` (`simulations_completed=1`, `simulations_failed=0`, `status_polls=7`):
`e2e_latency_ms=1900`, `server_latency_ms=133`, `accept_latency_ms=31`.

Both processes were then stopped by their **real** pid (owner of the listening socket —
10544 api, 17956 worker); afterwards no socket on 8080/8081, no `java` process, and both
jars open exclusively (no lock).

## Docs

- `docs/TECHNICAL_REPORT.md` — new `### PR-D3` entry and the progress header `PR-D0..PR-D3`.
- `README.md` — test-count line only (363 → 365; 350 api + 15 worker). The startup
  instructions already require the worker as a separate process and never mentioned an
  api auto-startup override, so **no startup-instruction change** was needed.
- `docs/AGENT_LESSONS.md` — two entries (11, 12) for real mistakes made during the
  sensitivity checks.

## Out of scope (later PRs)

- `KafkaConsumerConfig` stays ungated (api producer).
- No concurrency / partition / lease / poll / park / seek change; `@Disabled` placeholder stays.
- k6 re-baseline and `docs/PHASE_4_MEASUREMENTS.md` N-replica numbers → PR-D5.
- Kubernetes manifests / HPA → PR-D4.
