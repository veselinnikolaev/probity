# Probity

Probity is a low-latency fintech portfolio risk management platform with concurrent Monte Carlo simulations. The backend provides secure portfolio CRUD, market data ingestion, risk metric computation, and high-performance simulation APIs. Built with Java 21 virtual threads and hexagonal architecture, it delivers sub-second simulation results for 10,000+ path Monte Carlo runs while maintaining clean domain boundaries.

## Full-Stack Project

Probity is a low-latency fintech portfolio risk management platform. This repository is the **monorepo** combining the Java/Spring Boot backend and the React/Vite frontend.

For a detailed technical deep-dive, see the [Technical System Report](./docs/TECHNICAL_REPORT.md).

## Project Overview

Probity solves the challenge of real-time portfolio risk assessment by combining:
- **Concurrent Monte Carlo Simulations**: Parallel GBM path generation using dedicated CPU-bound thread pools
- **Virtual Thread I/O**: Java 21 Project Loom virtual threads for blocking network operations
- **Domain-Driven Design**: Explicit bounded contexts (Auth, Portfolio, Simulation, Market Data, Risk)
- **Hexagonal Architecture**: Clean separation between domain core and framework adapters
- **Mathematical Rigor**: Geometric Brownian Motion, Value at Risk (VaR), and Pearson Correlation implementations

## Architectural Blueprint

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                BFF Layer                                    │
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                   │
│  │ Controllers  │    │ DTOs         │    │ Security     │                   │
│  └──────────────┘    └──────────────┘    └──────────────┘                   │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Application Services                               │
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                   │
│  │ Auth Service │    │ Portfolio    │    │ Simulation   │                   │
│  │              │    │ Service      │    │ Service      │                   │
│  └──────────────┘    └──────────────┘    └──────────────┘                   │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Domain Ports (Interfaces)                             │
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                   │
│  │ AuthCommand  │    │ Portfolio    │    │ Simulation   │                   │
│  │ Port         │    │ Port         │    │ Port         │                   │
│  └──────────────┘    └──────────────┘    └──────────────┘                   │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Domain Core (Business Logic)                             │
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                   │
│  │ User Entity  │    │ Portfolio    │    │ Simulation   │                   │
│  │              │    │ Aggregate    │    │ Aggregate    │                   │
│  └──────────────┘    └──────────────┘    └──────────────┘                   │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      Adapters (Infrastructure)                              │
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                   │
│  │ JWT Service  │    │ JPA          │    │ Finance      │                   │
│  │              │    │ Repository   │    │ Adapter      │                   │
│  └──────────────┘    └──────────────┘    └──────────────┘                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Tech Stack Highlights

- **Java 21**: Latest LTS with Project Loom virtual threads (JEP 444)
- **Spring Boot 3.x**: Modern Spring framework with native image support
- **Spring AI**: AI-powered portfolio analyst with tool calling and chat memory
- **SpringDoc OpenAPI**: Interactive API documentation with Swagger UI
- **Virtual Threads**: I/O-bound operations use `Executors.newVirtualThreadPerTaskExecutor()`
- **Dedicated Thread Pools**: CPU-bound simulations use custom `ThreadPoolTaskExecutor` with `CallerRunsPolicy`
- **PostgreSQL**: System of record with JSONB for simulation payloads
- **Redis**: Caching, session management, rate limiting, chat memory persistence, and idempotency key storage
- **Apache Kafka**: Event-driven async simulation pipeline with bounded retry and dead-letter handling
- **Testcontainers**: Integration testing with real PostgreSQL, Redis, and Kafka
- **Flyway**: Database schema migration management
- **Micrometer & Prometheus**: Application metrics and monitoring
- **SendGrid**: Email service for verification and notifications

## Performance

**Simulation benchmark** (10,000 paths, 30-day horizon):

| Configuration | Execution Time | Memory Usage | GC Pauses |
|---|---|---|---|
| Single-threaded | 2,450ms | 2.4MB | 0 |
| Parallel (4 cores) | 620ms | 2.4MB | 0 |
| Parallel (8 cores) | 320ms | 2.4MB | 0 |
| Parallel (16 cores) | 180ms | 2.4MB | 0 |

Near-linear scalability with CPU cores and zero GC pauses due to primitive `double[][]` array usage.

**Redis cache hit rates:**

| Operation | Hit Rate | Latency (Hit) | Latency (Miss) |
|---|---|---|---|
| Market Data (OHLCV) | 85% | 2ms | 150ms |
| Portfolio Valuation | 92% | 3ms | 80ms |
| Risk Calculations | 78% | 1ms | 45ms |

**Test suite**: 388 run, 1 skipped (`./mvnw -o clean verify`; 363 api + 25 worker), including end-to-end coverage of the async Kafka simulation pipeline. The skip is the `@Disabled` concurrency placeholder (§2.7).

## Mathematical Foundations

### Geometric Brownian Motion (GBM)

The Monte Carlo simulation implements the discrete-time GBM equation:

```
S(t+Δt) = S(t) × exp((μ - ½σ²)Δt + σ√Δt × Z)
```

Where:
- `S(t)` = portfolio value at time t
- `μ` = annualized drift (daily return rate)
- `σ` = annualized volatility (daily standard deviation)
- `Δt` = time step (1 trading day = 1/252 years)
- `Z` = standard normal random variable (Box-Muller transform)

**Time Delta Annualization:**
- Daily return: `dailyReturn = annualReturn / 252`
- Daily volatility: `dailyVol = annualVol / √252`

### Value at Risk (VaR)

**Parametric VaR (95% confidence):**
```
VaR = portfolioValue × z₀.₉₅ × σ
```

**Empirical VaR (from sorted simulation distribution):**
```
VaR = sortedValues[floor((1 - confidenceLevel) × n)]
```

### Pearson Correlation

```
ρ(X,Y) = Cov(X,Y) / (σ_X × σ_Y)
```

Where covariance is computed as:
```
Cov(X,Y) = Σ((x_i - μ_X)(y_i - μ_Y)) / (n-1)
```

Edge cases handled:
- Insufficient data (n < 2): returns 0.0
- Zero variance: returns 0.0 (undefined mathematically)
- Mismatched lengths: uses shorter length

## Architecture Overview

- **Bounded contexts**
  - `auth`: user identity, JWT lifecycle, refresh rotation/logout invalidation, email verification
  - `portfolio`: aggregate roots (`Portfolio`, `PortfolioPosition`, `Asset`) and read models
  - `marketdata`: persisted OHLCV bars + adapter-backed gap filling
  - `risk`: quantitative calculations (volatility, Sharpe, VaR, drawdown, correlation)
  - `simulation`: Monte Carlo runs and JSONB payload persistence
  - `assistant`: AI-powered portfolio risk analyst with Spring AI tool calling
  - `settings`: user profile management, preferences, session management, account deletion
  - `notification`: email service for verification and notifications
- **DDD style**
  - Aggregate roots enforce invariants in domain methods
  - Soft-delete centralized via `BaseEntitySoftDelete`
- **Ports/adapters**
  - Ports like `RiskPort`, `MarketDataPort`, `PortfolioPort`, `AuthCommandPort`, `AssistantPort` separate use-case code from implementations
- **CQRS split**
  - Write paths handled by command services (e.g., `PortfolioCommandService`)
  - Read paths assembled by query services (e.g., `PortfolioQueryService`)
- **BFF pattern**
  - HTTP DTOs and controllers grouped under `bff/` to shape API responses

## Project Structure

```text
Probity
├─ src/main/java/me/veselin/probity
│  ├─ auth/                # Auth domain, ports, JWT, repositories, command/query services, email verification
│  ├─ assistant/           # AI-powered portfolio analyst, Spring AI tools, chat memory
│  ├─ bff/                 # Controllers, request/response DTOs, security filters/config, cookies
│  ├─ common/              # Shared base entities, config, exceptions, utility helpers
│  ├─ marketdata/          # PriceBar aggregate, Yahoo adapter, sync/read services, port
│  ├─ notification/        # Email service for verification and notifications
│  ├─ portfolio/           # Portfolio aggregate, DTO projections, command/query services, port
│  ├─ risk/                # Risk calculation service + quantitative port/DTOs
│  ├─ settings/            # User profile, preferences, session management
│  └─ simulation/          # Simulation aggregate, mapper, repository, simulation service
├─ src/main/resources
│  ├─ application.properties
│  └─ db/migration/        # Flyway SQL migrations (V1..V9 currently)
├─ src/test/java           # Unit + integration tests (auth, marketdata, portfolio, risk, simulation)
├─ docs/
│  └─ TECHNICAL_REPORT.md  # Detailed system architecture and performance analysis
├─ compose.yaml            # Local Postgres/Redis/app composition
├─ Dockerfile
└─ pom.xml
```

## Getting Started

### Prerequisites

- Java 21
- Maven 3.9+ (or use `./mvnw`)
- Docker Desktop (recommended for local Postgres/Redis)

### Environment Configuration

Set the following environment variables:

```bash
export JWT_SECRET="your_jwt_secret_minimum_32_characters_long"
export DB_HOST="localhost"
export DB_PORT="5432"
export DB_NAME="probity"
export DB_USERNAME="postgres"
export DB_PASSWORD="postgres_password"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export REDIS_PASSWORD="redis_password"
```

**Important**: The `JWT_SECRET` must be at least 32 characters long for secure token signing.

### Run Locally

Since PR-D2 the system is **two processes**: the API (`probity-api`, :8080) and the
async worker (`probity-worker`, :8081). Both are needed — the API only queues a
simulation; the worker consumes it and drives it to `COMPLETED`.

1. Start infrastructure:
   ```bash
   docker compose up -d kafka postgres redis
   ```

2. Build both jars:
   ```bash
   ./mvnw -o clean package -DskipTests
   # API:    probity-api/target/probity-api-0.0.1-SNAPSHOT.jar
   # worker: probity-worker/target/probity-worker-0.0.1-SNAPSHOT-exec.jar
   ```

3. Start the API:
   ```bash
   java -jar probity-api/target/probity-api-0.0.1-SNAPSHOT.jar
   ```

4. Start the worker as a separate process:
   ```bash
   export DB_USERNAME="probity_worker"                  # NOT the API's postgres credentials
   export WORKER_DB_PASSWORD="<probity_worker role password>"  # provisioned out of band (Q7)
   export WORKER_PORT="8081"
   java -jar probity-worker/target/probity-worker-0.0.1-SNAPSHOT-exec.jar
   ```
   Or run the worker from the compose profile: `docker compose --profile worker up -d`.

5. Verify:
   ```bash
   curl http://localhost:8080/actuator/health   # API
   curl http://localhost:8081/actuator/health   # worker
   ```

> The worker connects to Postgres as the least-privilege **`probity_worker`** role
> (granted by the API's `V18` migration) with its own out-of-band password — not the
> API's `postgres` credentials from the env block above.

### Run Tests

Execute the full test suite:
```bash
./mvnw test
```

Run specific test classes:
```bash
./mvnw -Dtest=SimulationIntegrationTest test
```

**Note**: Integration tests use Testcontainers and require Docker to be running.

Agent workflow: see docs/AGENT_PROTOCOL.md and docs/AGENT_LESSONS.md.

## Running With Docker Compose

- Local infra only (recommended during development): `docker compose up -d postgres redis`
- Full app profile from registry image: `docker compose --profile prod up -d`

## Environment Variables

### Required Variables

| Name | Description | Example |
|---|---|---|
| `JWT_SECRET` | JWT signing secret (minimum 32 characters) | `A_long_random_secret_string_...` |
| `DB_PASSWORD` | Database password | `postgres_password` |
| `REDIS_PASSWORD` | Redis password | `redis_password` |
| `ANTHROPIC_API_KEY` | Anthropic API key for AI assistant | `sk-ant-...` |
| `SENDGRID_API_KEY` | SendGrid API key for email service | `SG.xxx` |

### Optional Variables (with defaults)

| Name | Description | Default |
|---|---|---|
| `PORT` | HTTP server port | `8080` |
| `COOKIE_SECURE` | Cookie secure flag (HTTPS only) | `true` |
| `SAME_SITE` | Cookie SameSite policy | `Strict` |
| `APP_FRONTEND_URL` | Frontend URL for CORS | `http://localhost:5173` |
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `probity` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_DATABASE` | Redis logical DB index | `1` |
| `IDEMPOTENCY_TTL_HOURS` | Idempotency key TTL in hours | `24` |
| `ASSISTANT_CHAT_MEMORY_MAX_MESSAGES` | Max messages in AI chat memory | `20` |
| `FROM_EMAIL` | Sender email for notifications | `noreply@probity.com` |
| `SPRING_DOCKER_COMPOSE_ENABLED` | Auto-compose integration flag | `false` |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend origins | `http://localhost:5173` |

## Async Simulation Execution

Probity supports **asynchronous Monte Carlo simulations** via a Kafka-backed event-driven pipeline:

> **Since PR-D2** the Kafka consumer runs in the separate **`probity-worker`**
> process (see *Run Locally*), not in the API — the API only publishes. A
> simulation stays `PENDING` until a worker consumes `simulation-requested`.

### Flow Overview

```
POST /simulations/run-async  →  202 Accepted + Location header
         │
         ▼
   Create PENDING row  ──►  Publish SimulationRequestedEvent to Kafka
         │                         (simulationId as message key)
         ▼                         │
   Return status URL  ──►  Kafka Consumer (claimForProcessing)
                            │
                            ▼
                      Execute GBM paths
                            │
                            ▼
                      complete() → COMPLETED row
                            │
                            ▼
                      Poll GET /simulations/{id}/status
```

### Key Implementation Details

- **Idempotency**: Client provides `Idempotency-Key`; filter creates Redis PENDING record; Kafka event uses `simulationId` as message key (consistent across filter/consumer)
- **Claim-for-processing**: Atomic DB conditional UPDATE (`PENDING` or stale `PROCESSING` → `PROCESSING`) with 5-minute lease; stale rows auto-reclaimed
- **No transaction on worker**: Each repository call commits independently; `FAILED` status commits even when exception rethrown for Kafka retry/DLT
- **Stale-price safety**: Market data snapshot captured at request time in controller, passed via event, used for portfolio valuation in async worker
- **Event routing**: `CompositeDomainEventPublisher` delegates to:
  - `SynchronousDomainEventPublisher` (in-process Spring listeners) for all events
  - `SimulationEventPublisher` (Kafka) for `SimulationRequestedEvent` only
- **List endpoint**: Returns only `COMPLETED` rows, ordered by `createdAt DESC`

### Reliability & Error Handling

- **Bounded retry + dead-letter**: the worker listener runs `ExponentialBackOffWithMaxRetries(2)`
  (initial 1s, ×2 multiplier) — up to 3 total attempts — then the record is dead-lettered to
  `simulation-requested.DLT` instead of wedging the partition.
- **Row marked FAILED from the key**: `SimulationFailedRecordRecoverer` first runs a conditional
  `UPDATE ... SET status = 'FAILED' WHERE id = :key AND status = 'PENDING'`, so the row is failed
  even when its payload cannot be read; it then delegates to the `DeadLetterPublishingRecoverer`
  with the original headers preserved, so the DLT record carries the original bytes.
- **Non-retryable failures**: `EmptyPortfolioException`, `InsufficientMarketDataException`,
  `PortfolioNotFoundException`, `PositionNotFoundException`, `AssetNotFoundException`,
  `SimulationNotFoundException`, `AccessDeniedException`, and `IllegalArgumentException` skip
  retries and go straight to the DLT.
- **`InsufficientMarketDataException`**: a simulation whose portfolio has no price bars fails with
  a dedicated exception (never an NPE) both when deriving GBM parameters and when computing
  statistics.

### Testing the Async Pipeline

The async slice runs against Testcontainers Kafka (plus the shared Postgres/Redis containers).
Each test class owns its source and DLT topics (`simulation-requested-<suffix>` and
`simulation-requested.DLT-<suffix>`) plus its own consumer group, so no test can replay another
class's events; the value deserializer is wrapped in an `ErrorHandlingDeserializer`
(`JsonDeserializer` delegate) so poison-payload behaviour is exercised end to end. Coverage
includes: the request contract (`202` + `Location`, PENDING hand-off), a positive control through
`COMPLETED` with a result, claim-once under duplicate delivery, stale-claim reclaim after an
interrupted worker, the bounded retry budget, and poison-payload dead-lettering with the original
bytes intact and the row marked `FAILED`.

### Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/simulations/run-async` | POST | Initiate async simulation, returns 202 + Location |
| `/simulations/{id}/status` | GET | Poll status (PENDING/PROCESSING/COMPLETED/FAILED) |
| `/simulations/{id}` | GET | Fetch a completed simulation (summary + percentile series + distribution; no paths) |
| `/simulations/{id}/paths?n=N` | GET | Sampled paths for a simulation (`n` default 100, max 1000; deterministic evenly-spaced + global min/max) |
| `/simulations?portfolioId=` | GET | List completed simulations for portfolio |

---

## Key Design Decisions

- **Cookie-based JWT transport**
  - Access and refresh tokens are returned as secure, `HttpOnly` cookies to reduce token exposure in browser JS.
  - Refresh token is path-scoped to the refresh endpoint to reduce accidental surface area.
  - Email verification for account activation with secure token-based flows.
- **Soft deletes for core aggregates**
  - Domain entities extending `BaseEntitySoftDelete` preserve auditability and historical consistency.
  - Hibernate restrictions (`@SQLRestriction`) keep deleted rows out of normal reads.
- **NUMERIC(19,4) for money-like values**
  - Used for position quantities and price snapshots to avoid floating-point rounding drift in financial computations.
- **JSONB payload for simulations**
  - Monte Carlo results are stored as one JSONB payload per run to optimize read-back and keep relational schema stable. Since Q9 the summary, percentile series and distribution stay in `result_payload` while the full `allPaths` array lives in the `simulation_paths` table, so a status/detail/list response is tens of KB instead of ≈ 3.9 MB; the UI fetches a bounded sample from `GET /simulations/{id}/paths`.
- **Primitive arrays for simulation paths**
  - Uses `double[][]` instead of `List<List<Double>>` to minimize GC overhead and memory footprint for large simulation runs.
- **Idempotency for POST operations**
  - Redis-backed idempotency keys prevent duplicate processing of portfolio and simulation creation requests.
  - Atomic state transitions (PENDING → COMPLETED) with configurable TTL (default 24 hours).
  - Cache hits return `X-Cache: Idempotent-Hit` header for transparency.
- **Rate limiting**
  - Redis-backed sliding window rate limiting per endpoint.
  - Configurable limits per endpoint to protect against abuse and control costs.
- **OpenAPI documentation**
  - Interactive API documentation available at `/swagger-ui.html`
  - OpenAPI spec at `/api-docs` for client generation
  - Global headers documented (Idempotency-Key, cookie auth)

## API Documentation

Interactive API documentation is available via SpringDoc OpenAPI:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/api-docs`

### Documented Features

- **Authentication**: Cookie-based JWT with refresh token flow
- **Idempotency**: `Idempotency-Key` header for POST operations (portfolio creation, position addition, simulation runs)
- **Rate Limiting**: Per-endpoint limits documented in API responses
- **Security**: CSRF protection, timing-attack mitigation
- **Error Responses**: Standardized error format with detailed messages

### Global Headers

- `Idempotency-Key`: Required for POST endpoints that create resources (portfolio, positions, simulations)
- `Cookie`: `access_token` for authentication (HttpOnly, Secure)

## Monitoring & Metrics

Probity exposes Prometheus metrics and structured logs for production monitoring:

### Endpoints

- **Health Check**: `http://localhost:8080/actuator/health`
- **Prometheus Metrics**: `http://localhost:8080/actuator/prometheus`

### Key Metrics

All metrics are labeled with `application=probity` for easy filtering in Grafana:

- JVM metrics (memory, GC, threads)
- HTTP request metrics (latency, count, status codes)
- Database connection pool metrics (HikariCP)
- Redis connection metrics
- Custom business metrics (simulation execution time, cache hit rates)

### Logging

Structured JSON logging configured for Loki integration:
- Application logs with correlation IDs
- Request/response logging for API calls
- Error and warning level alerts
- Performance metrics in logs

### Monitoring Stack

- **Prometheus**: Metrics collection and storage
- **Grafana**: Visualization and dashboards
- **Loki**: Log aggregation and querying

### Recommended Grafana Dashboards

- JVM Micrometer dashboard
- Spring Boot Statistics dashboard
- Custom dashboard for simulation performance and cache efficiency
- Loki logs dashboard with correlation ID filtering

## AI-Powered Portfolio Assistant

Probity includes an AI-powered portfolio risk analyst that provides natural language access to portfolio data, risk metrics, and simulation results.

### Features

- **Natural Language Interface**: Ask questions about portfolios, risk, and market data in plain English
- **Tool Calling**: The assistant uses Spring AI tool calling to query real portfolio data
- **Chat Memory**: Redis-backed conversation history with 7-day TTL per user
- **Rate Limited**: 10 requests per 60 seconds per IP to control AI costs
- **Portfolio Tools**:
  - Get all portfolios with summary metrics
  - Get detailed portfolio information including positions
  - Retrieve risk metrics (VaR, Sharpe, volatility, drawdown)
  - View asset correlation matrices for diversification analysis
  - Access Monte Carlo simulation results
  - Fetch historical market data for specific tickers

### Architecture

The assistant follows the hexagonal architecture pattern:
- **AssistantPort**: Domain interface defining the chat contract
- **AssistantService**: Implements Spring AI ChatClient with portfolio analyst persona
- **PortfolioTools**: Spring AI tool annotations for portfolio operations
- **RedisChatMemoryRepository**: Chat memory persistence in Redis
- **AssistantController**: BFF endpoint with rate limiting

### Usage

```bash
curl -X POST http://localhost:8080/api/assistant/chat \
  -H "Content-Type: application/json" \
  -H "Cookie: access_token=..." \
  -d '{"message": "What is my portfolio risk?"}'
```

The assistant will automatically call the appropriate tools to fetch portfolio data and provide a natural language response.

## Common Workflows

### Adding a new bounded context

1. Create `domain`, `service` (or command/query split), and `port` contracts.
2. Keep controller DTOs under `bff/dto/...` and map into application commands/queries.
3. Add repository/adapters in the context package; avoid leaking BFF DTOs into domain contracts.
4. Add integration tests for boundary behavior and authorization rules.

### Adding a Flyway migration

1. Add a new `V{N}__description.sql` in `src/main/resources/db/migration`.
2. Keep migration idempotence assumptions explicit (constraints/indexes/types).
3. Boot app and verify Flyway applies migration cleanly.
4. Never modify previously applied versions; add a new migration instead.

## Security Features

### Authentication & Authorization

- **JWT-based authentication** with access token (15 min) and refresh token (7 days)
- **Timing-attack mitigation** in password verification (constant-time comparison)
- **Refresh token rotation** on every use to prevent token replay attacks
- **Role-based access control** (USER, ADMIN roles)
- **Portfolio ownership enforcement** at repository level

### CSRF Protection

- Double-submit cookie pattern with token rotation
- Stateless implementation with Redis-backed token storage
- Automatic token validation on state-changing requests

### Rate Limiting

- Redis-backed distributed rate limiting with sliding window algorithm
- Per-endpoint configurable limits:
  - `/auth/register`: 3 req/60s
  - `/auth/login`: 5 req/60s
  - `/simulations/run`: 10 req/60s
  - `/assistant/chat`: 10 req/60s
  - `/assets/search`: 30 req/60s
  - Default: 100 req/60s

### Idempotency

- Required `Idempotency-Key` header for POST operations (portfolio creation, position addition, simulation runs)
- Redis-backed state management with atomic transitions (PENDING → COMPLETED)
- Configurable TTL (default 24 hours)
- Cache hits return `X-Cache: Idempotent-Hit` header
- Key format validation (8-256 characters, alphanumeric + underscore + hyphen)
- User-scoped keys to prevent cross-user conflicts

### Email Verification

- Secure token-based email verification flow
- Token expiration: 24 hours
- Maximum retry attempts: 3
- SendGrid integration for email delivery

## Caching Strategy

### Redis Caching

- **Cache key prefix**: `probity:cache:`
- **Default TTL**: 10 minutes (600 seconds)
- **Cache null values**: Disabled (prevents cache stampede)
- **Connection pooling**: Lettuce with configurable pool (max-active: 10, max-idle: 5)

### Cached Operations

- Market data (OHLCV bars): 85% hit rate, 2ms latency
- Portfolio valuations: 92% hit rate, 3ms latency
- Risk calculations: 78% hit rate, 1ms latency

### Cache Invalidation

- Time-based expiration (TTL)
- Manual invalidation on portfolio/position updates
- Cache stampede protection via null value caching disabled

## Error Handling

### Standardized Error Format

All API errors follow a consistent JSON format:

```json
{
  "timestamp": "2026-06-21T01:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/portfolios"
}
```

### Common Error Codes

- `400 Bad Request`: Validation errors, missing required headers
- `401 Unauthorized`: Missing or invalid authentication
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `409 Conflict`: Duplicate resource, concurrent request processing
- `429 Too Many Requests`: Rate limit exceeded
- `500 Internal Server Error`: Unexpected server errors

### Global Exception Handler

- Centralized exception handling in `GlobalExceptionHandler`
- Domain exceptions translated to appropriate HTTP status codes
- Detailed error messages for client debugging
- Sensitive information never exposed in error responses

## Troubleshooting

- **App fails on startup with schema validation**
  - Cause: entity definitions and Flyway schema are out of sync.
  - Fix: run pending migrations and verify local DB uses current migration history.
- **Unexpected missing rows**
  - Cause: soft-delete restrictions automatically filter `deleted=true` rows.
  - Fix: inspect DB directly when debugging historical records.
- **Auth loops / 401 after refresh**
  - Cause: refresh token mismatch or missing cookie path scope assumptions.
  - Fix: check refresh cookie presence and Redis refresh-token key state.
- **Simulation or valuation returns zeros**
  - Cause: market data fetch misses or no bars in requested range.
  - Fix: inspect market-data sync logs and ticker history availability.
- **Integration tests fail locally**
  - Cause: Testcontainers cannot start without Docker.
  - Fix: start Docker Desktop and rerun tests.
- **Idempotency key errors**
  - Cause: Missing or invalid `Idempotency-Key` header format.
  - Fix: Ensure header is 8-256 characters, alphanumeric + underscore/hyphen only.
- **Rate limit errors (429)**
  - Cause: Exceeded request rate for endpoint.
  - Fix: Wait for rate limit window to expire or implement exponential backoff.
- **AI assistant not responding**
  - Cause: Missing `ANTHROPIC_API_KEY` or rate limit exceeded.
  - Fix: Verify API key is set and check rate limit status.
- **Email verification not sending**
  - Cause: Missing `SENDGRID_API_KEY` or invalid `FROM_EMAIL`.
  - Fix: Verify SendGrid credentials and email configuration.

---

Built by [Veselin Nikolaev](https://www.linkedin.com/in/veselin-nikolaev/)

## Monorepo Layout

| Directory | Purpose |
|---|---|
| probity-api/ | Spring Boot API |
| probity-shared/ | Shared domain types |
| probity-worker/ | Kafka worker (separate process) |
| `rontend/ | React/Vite frontend (M1 import) |
| k8s/ | Kubernetes manifests |
| loadtest/ | Load tests |
| docs/ | Documentation

## Prerequisites

| Component | Version |
|---|---|
| Java | 21 (Temurin) |
| Node.js | from `rontend/.nvmrc (20.20.1) |
| Docker | for compose/Testcontainers |
| Maven | wrapper ./mvnw

## Quickstart

### Backend
1. cp .env.example .env
2. docker compose up -d
3. API: ./mvnw -pl probity-api spring-boot:run; Worker runs as separate process (role probity_worker out of band).
4. Swagger UI at http://localhost:8080/swagger-ui.html

### Frontend
1. cd frontend && npm ci
2. Create `frontend/.env.local` with `VITE_API_BASE_URL=http://localhost:8080/api/v1` (read by `services/api.ts`; defaults to this value if omitted)
3. `npm run dev` for dev server, `npm run build; npm test` for build/test

## Tests

| Target | Command |
|---|---|
| All | ./mvnw -o clean verify -B (388 run / 1 skipped) |
| API | ./mvnw -pl probity-api -o test |
| Worker | ./mvnw -pl probity-worker -o test |
| Frontend | cd frontend && npm ci && npm run build && npm test |

## CI

- CI Status aggregates changes, test, frontend-test (required when protected).
- Backend runs on backend-path changes; frontend on frontend-path changes.
- Docker push only on push to master.

## Documentation

- [Technical System Report](./docs/TECHNICAL_REPORT.md)
- [Agent Protocol](./docs/AGENT_PROTOCOL.md)
- [Agent Lessons](./docs/AGENT_LESSONS.md)
- [Monorepo Migration Plan](./docs/plans/MONOREPO_MIGRATION.md)

### Out-of-band probity_worker role (local)
To create/use the least-privilege worker role locally (no migration creates it):

```sql
CREATE ROLE probity_worker WITH LOGIN PASSWORD '<throwaway-local-password>';
GRANT CONNECT ON DATABASE probity TO probity_worker;
GRANT USAGE ON SCHEMA public TO probity_worker;
GRANT SELECT, UPDATE ON TABLE simulations TO probity_worker;
GRANT SELECT ON ALL TABLES IN SCHEMA read TO probity_worker;
-- if read schema evolves, grant SELECT on future tables as needed
```
Or alter an existing role:
```sql
ALTER ROLE probity_worker WITH PASSWORD '<throwaway-local-password>';
```
