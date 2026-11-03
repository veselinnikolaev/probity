# ADR 0009 — PR-D3: SimulationRequestedEventConsumer gate in monolith

**Status:** resolved — consumer bean gated (PR #13, merged).

## Context

Before PR-D3, the same JVM both served HTTP and consumed `simulation-requested`. The `202 Accepted` path was fast, but the completion write made the consumer the throughput bottleneck. The design needed a way to disable the listener in the monolith without deleting it.

## Decision

Gate the consumer bean with `@ConditionalOnProperty(name = "probity.simulation.worker.enabled", matchIfMissing = true)` so the monolith simply does not start it (PHASE_4_WORKER_EXTRACTION.md lines 237-240, `application.yaml` default `true`). Deploy the API with the flag `false` (or a `monolith` profile) and the worker with it `true`. The producer (`SimulationEventPublisher`) stays in the API as the hand-off; the synchronous path (`POST /simulations/run`) never touches the listener and is unchanged.

## Consequences

- API and worker can scale, deploy and restart independently.
- The worker cannot import API classes (module dependency direction), so the async path cannot accidentally depend on web/security code.
- The API and worker share a database schema. That is a deliberate coupling: the worker reads portfolios/positions/assets and writes simulations directly. Cross-module payload enrichment is deferred (see ADR 0005 Q5).
- Extraction of a future service is possible per bounded context without a rewrite, because the ports already separate domain from transport/persistence.

## Evidence

- PHASE_4_WORKER_EXTRACTION.md lines 237-240: gate the consumer bean with `@ConditionalOnProperty`.
- `application.yaml` `probity.simulation.worker.enabled` defaults `true`; set `false` to run the consumer inside the API monolith.
- Only `probity-worker` declares a `@KafkaListener` (`SimulationRequestedEventConsumer.java:58,70`); the API has none.
- `docs/architecture.md` §3: domain package layout and dependency direction.

[Link: PHASE_4_WORKER_EXTRACTION.md lines 237-240]