# ADR 0001 — Modular monolith with ports and adapters

**Status:** accepted (in place since the initial schema).

## Context

Probity combines an authenticated HTTP API, portfolio/risk read models, an LLM
assistant, and a compute-heavy simulation workload. A full microservice split would add
network boundaries, versioned contracts, and operational overhead before there is any
evidence that the domains scale differently. However, the simulation workload *does* have
a different scaling and failure profile from the request/response API (long CPU-bound
work, Kafka delivery, slow completion writes).

## Decision

Keep one deployment unit for the API and domain, structured as a modular monolith with
hexagonal boundaries around the domain:

- Three Maven modules: `probity-shared` (domain + persistence kernel), `probity-api`
  (HTTP BFF and application services), `probity-worker` (the one process split, see ADR
  0002). `pom.xml:23-27`.
- Domain types live in `probity-shared`; HTTP DTOs, security filters, and controllers
  live in `probity-api`; the worker depends on the shared kernel, never on the API.
- Ports (`SimulationPort`, `MarketDataPort`, `RiskPort`, `DomainEventPublisher`) are
  interfaces owned by the domain side; adapters are JPA repositories, Kafka publishers,
  and the Monte Carlo service.

## Consequences

- One build, one migration chain, one set of domain invariants.
- The worker cannot import API classes (module dependency direction), so the async path
  cannot accidentally depend on web/security code.
- The API and worker share a database schema. That is a deliberate coupling: the worker
  reads portfolios/positions/assets and writes simulations directly. Cross-module
  payload enrichment is deferred (see ADR 0002, Q1).
- Extraction of a future service is possible per bounded context without a rewrite,
  because the ports already separate domain from transport/persistence.

## Evidence

- Module list and dependency direction: `pom.xml:23-27`, `probity-api/pom.xml`,
  `probity-worker/pom.xml`.
- Only `probity-worker` declares a `@KafkaListener`
  (`SimulationRequestedEventConsumer.java:58,70`); the API has none.
- The worker has no Redis dependency: the portfolio query path is not `@Cacheable`
  (`docs/architecture.md` §3).
