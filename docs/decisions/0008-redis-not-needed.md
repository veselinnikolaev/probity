# ADR 0008 — Q8: Redis not needed by worker

**Status:** resolved — confirmed not needed (PR-D0).

## Context

The worker execution path (Monte Carlo simulation, claim/complete, Kafka consumption) does not use Redis for caching or any other purpose.

## Decision

Redis is **confirmed not needed** by the worker execution path. The worker has no `@Cacheable` annotations, no Redis-backed session state, and no idempotency keys stored in Redis (PHASE_4_WORKER_EXTRACTION.md line 261; `docs/architecture.md` §3). Flag if a future `@Cacheable` is added to the portfolio read.

## Consequences

- No Redis connection configuration is required for the worker profile.
- Worker deployment is simplified: no Redis dependency in `Dockerfile.worker` or compose profiles.
- If future development adds `@Cacheable` to portfolio read queries, a Redis dependency must be added and the Q8 decision revisited.

## Evidence

- PHASE_4_WORKER_EXTRACTION.md line 261: "Q8 - Redis: OPEN. Confirmed not needed by the worker execution path; flag if a future `@Cacheable` is added to the portfolio read."
- `docs/architecture.md` §3: "The worker has no Redis dependency."
- Worker `application.yaml` has no `spring.data.redis.*` or `spring.redis.*` properties.

[Link: PHASE_4_WORKER_EXTRACTION.md line 261]