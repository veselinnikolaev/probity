# Architecture

Probity is a portfolio risk platform: authenticated portfolio CRUD, market-data
ingestion, risk metrics, and Monte Carlo simulation, with an async simulation path that
runs on a separately deployable worker.

Three Maven modules (`pom.xml:23-27`), Java 21, Spring Boot 3.5.11:

| Module | Role | Entry point |
|---|---|---|
| `probity-shared` | Domain aggregates, JPA entities/repositories, shared config and DTOs | — (library) |
| `probity-api` | HTTP BFF: auth, portfolios, market data, assistant, simulation API | `ProbityApplication` |
| `probity-worker` | Kafka consumer that executes simulations and writes results | `WorkerApplication` |

## 1. Why this shape

A modular monolith with hexagonal (ports & adapters) boundaries around the domain, and
exactly one process split — the simulation worker — chosen because that workload has a
different scaling and failure profile from the HTTP API (ADR
[0001](decisions/0001-modular-monolith.md), [0002](decisions/0002-async-simulation-worker.md)).
The split is real: only `probity-worker` declares a `@KafkaListener`
(`SimulationRequestedEventConsumer.java:58,70`).

Package layout:

```
probity-api/src/main/java/me/veselin/probity/
  bff/          controllers, DTOs, security filters, rate limiting, idempotency
  auth/         registration, login, refresh rotation, email verification, profile
  portfolio/    portfolios, positions, valuation, risk read models
  simulation/   simulation port/adapter into the shared domain + status reads
  assistant/    Spring AI chat over portfolio tools, Redis-backed chat memory
  notification/ SendGrid email
  common/       config, error handling, routes

probity-worker/src/main/java/me/veselin/probity/
  simulation/   consumer, Monte Carlo service, claim/complete repository, DLT recoverer
  portfolio/    read-side portfolio loading for a run
  risk/         volatility, VaR/CVaR, correlation
  marketdata/   price bars
  common/       Kafka config, properties
```

## 2. Request flows

### Synchronous simulation

`POST /api/v1/simulations/run` → `SimulationController.run` → `SimulationPort` →
`MonteCarloSimulationService.run` → persist → `201` with the result. The HTTP request
owns the whole compute; it is the simplest path and the one used by the tests that assert
the math.

### Asynchronous simulation (the default in the UI)

```
client ──POST /simulations/run-async──> api ──PENDING row──> Postgres
                                          │
                                          └─publish──> Kafka topic `simulation-requested` (6 partitions)
                                                             │
                                        worker (1 record/poll) ─> claim (fenced, 12 min lease)
                                                             │
                                                    Monte Carlo (parallel paths)
                                                             │
                                   complete() in one tx ──> result_payload + simulation_paths
                                                             │
client ──GET /simulations/{id}/status (poll) <── COMPLETED row │
client ──GET /simulations/{id}/paths?n=100 ──> sampled paths ──┘
```

- Accept is fast: broker write + `PENDING` insert, `202` with a `Location` header.
- Failure path: retries with exponential backoff, then `simulation-requested.DLT`; the
  recoverer marks the row `FAILED` if it is still `PENDING`
  (`SimulationFailedRecordRecoverer`), and a counter
  `probity_simulation_dlt_publish_failures_total` records DLT publish failures.
- Claim/lease invariant: `claim-lease = 12m` > `max.poll.interval.ms = 10m`, so a
  legitimate slow run is never reclaimed while it is still working
  (`application.yaml`, ADR 0002). A reclaimed row is fenced: `complete()` only writes if
  the fence token still matches (`MonteCarloSimulationService.java:231-233`).
- Graceful `SIGTERM` completes the in-flight record before the consumer closes
  (measured: 0.53 s, `docs/performance.md` §Rolling restart).

## 3. Domain and data

Aggregates: `Portfolio` (positions, valuation), `Asset` / `PriceBar` (market data),
`Simulation` (status, result payload, sampled paths), `User` (credentials, preferences,
verification). Persistence is Postgres 16 via Spring Data JPA; schema is owned by Flyway
migrations `V1..V19` in `probity-api/src/main/resources/db/migration/`.

Simulation result storage is split (ADR [0003](decisions/0003-result-payload-storage.md)):

| Table | Contents |
|---|---|
| `simulations` | status, inputs, summary/percentiles/distribution, `result_payload` (≈ 11.6 KB) |
| `simulation_paths` | one row per simulation holding all paths (≈ 1.46 MB) |

Redis is not a cache tier. It holds refresh-token state, email-verification tokens,
idempotency keys, rate-limit counters, and assistant chat memory
(`IdempotencyFilter`, `RedisChatMemoryRepository`). There is no `@Cacheable` or
`CacheManager` in the codebase.

## 4. Security model

- Stateless requests with a JWT access token in an `HttpOnly` cookie; refresh token
  rotation in Redis; CSRF double-submit cookie enforced on state-changing routes except
  login/register/verify/resend (`SecurityConfiguration.java:63-76`).
- Idempotency: `Idempotency-Key` header on create/run operations, Redis-backed, 24 h TTL
  (`IdempotencyFilter`, `IDEMPOTENCY_TTL_HOURS`).
- Per-endpoint rate limits (`@RateLimit`, e.g. 30 requests/60 s on asset search and the
  assistant) returning `429`.
- Ownership checks on every portfolio/simulation read; the worker connects as a
  least-privilege role (`V18__worker_role_grants.sql`: `SELECT`/`UPDATE` on `simulations`
  and `SELECT` on the read schema only, no `users` access).
- Passwords are BCrypt-hashed; emails are verified before login.

## 5. Quantitative core

- Geometric Brownian Motion per path, seeded from historical daily returns/volatility of
  the portfolio's positions, executed over a fixed-size `double[][]` (primitive arrays,
  no boxing) on a dedicated executor (`MonteCarloSimulationService.runParallelPaths`).
- Empirical VaR and CVaR from the sorted terminal values, delegated to `RiskCalculator`
  as the single source of truth (`MonteCarloSimulationService.java:572-580`).
- Annualised volatility, rolling volatility, Pearson correlation with explicit edge-case
  handling (insufficient data, zero variance, mismatched lengths).
- The response carries summary statistics, an outcome distribution, percentile bands,
  and — moved out of the main payload by ADR 0003 — sampled paths.

## 6. Deployment topology

- `Dockerfile` builds the API image; `Dockerfile.worker` builds the worker image.
- `compose.yaml` runs Postgres, Redis, Kafka (KRaft, single node), plus optional
  Prometheus/Grafana/Loki; `app` is behind the `prod` profile and `simulation-worker`
  behind the `worker` profile.
- `k8s/` holds the worker Deployment (2 replicas, `terminationGracePeriodSeconds: 660`,
  probes, 500m/1Gi requests, 2/2Gi limits), a Service and an HPA. The API is not
  manifested in this repo.
- CI (`.github/workflows/ci_cd.yaml`): `Detect changes` → `Build & Test` (Maven on JDK 21)
  and `Frontend Build & Test` (Node from `frontend/.nvmrc`), aggregated by the required
  `CI Status` check; `Build & Push Docker Image` runs only on a push to `master`.

## 7. Known gaps

These are documented rather than hidden; see `docs/operations.md` §Known gaps and
`docs/performance.md` for evidence.

- No distributed tracing and no trace ID in logs.
- No HTTP latency histograms (only `_count`/`_sum`/`_max`), no Kafka consumer-lag meter,
  and zero alerting rules.
- Loki does not start with the current config, and nothing ships logs to it.
- The README's earlier performance and cache claims were not reproducible and have been
  removed (see `docs/performance.md`).
