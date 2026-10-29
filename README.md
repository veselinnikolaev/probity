# Probity

Portfolio risk platform: authenticated portfolio CRUD, market-data ingestion, risk metrics, and Monte Carlo simulation with an async simulation path on a separately deployable worker.

Three Maven modules (`pom.xml:23-27`), Java 21, Spring Boot 3.5.11:
- `probity-shared` — domain aggregates, JPA entities/repositories, shared config and DTOs (library)
- `probity-api` — HTTP BFF: auth, portfolios, market data, assistant, simulation API
- `probity-worker` — Kafka consumer that executes simulations and writes results

## Quickstart

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Build API + worker jars (offline first, then build)
./mvnw -o clean verify -B   # 363 tests, 0 failures, 0 skipped (api: 363, worker: 25 with 1 skipped)

# 3. Run the API (port 8080) and worker (port 8081)
java -jar probity-api/target/probity-api-0.0.1-SNAPSHOT.jar
# worker: ./mvnw -pl probity-worker spring-boot:run   OR   docker compose --profile worker up -d
```

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- API health: <http://localhost:8080/actuator/health>

## Documentation

New documentation layout for an external portfolio-evaluator audience:

- `docs/architecture.md` — system architecture, request flows, domain/data, security model, deployment topology
- `docs/performance.md` — measured results with method, units, one-dev-laptop caveats, and "what is not claimed"
- `docs/decisions/` — short ADRs (0001-modular-monolith.md, 0002-async-simulation-worker.md, 0003-result-payload-storage.md) with context, decision, consequences, measured evidence
- `docs/operations.md` — run/configure/deploy/observability; verified gaps (no _bucket, no Kafka lag meter, 0 rules, Loki permission denied, no shipper)
- `.github/agents/CONTRIBUTING.md` — agent protocol, link to agent docs in one line

## Endpoints (selected)

- `POST /api/v1/auth/{login,register,logout,refresh,verify,resend-verification}`
- `GET /api/v1/users/me [{,/password}, {,/preferences}, {,/sessions}]`
- `POST /api/v1/portfolios + {positions,volatility,alerts,composition,summary,risk-metrics,correlation,var}`
- `GET /api/v1/simulations/run, run-async, {id}, {id}/status, {id}/paths?n=N`
- `GET /api/v1/assets/search`

## Tests

Full test suite: `./mvnw -o clean verify -B` (363 api tests + worker tests, 1 skipped).

CI: `Detect changes` → `Build & Test` (Maven on JDK 21) and `Frontend Build & Test` (Node from `frontend/.nvmrc`), required check `CI Status`; `Build & Push Docker Image` on master push only.