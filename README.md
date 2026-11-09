# Probity

Portfolio risk platform: authenticated portfolio CRUD, market-data ingestion, risk metrics, and Monte Carlo simulation with an async simulation path on a separately deployable worker.

Three Maven modules (`pom.xml`), Java 21, Spring Boot 3.5.16:
- `probity-shared` — domain aggregates, JPA entities/repositories, shared config and DTOs (library)
- `probity-api` — HTTP BFF: auth, portfolios, market data, assistant, simulation API
- `probity-worker` — Kafka consumer that executes simulations and writes results

## Quickstart

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Build API + worker jars (offline first, then build)
./mvnw clean verify -B   # formatting, tests, coverage gates, and JaCoCo reports (416 tests, 1 skipped)

# 3. Run the API (port 8080) and worker (port 8081)
java -jar probity-api/target/probity-api-0.0.1-SNAPSHOT.jar
# worker: ./mvnw -pl probity-worker spring-boot:run   OR   docker compose --profile worker up -d
```

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- API health: <http://localhost:9091/actuator/health> (management interface)

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

Full test suite: `./mvnw clean verify -B` (366 API tests; 50 worker tests, 1 skipped).

CI runs pinned GitHub Actions, full-history Gitleaks, Semgrep security rules, Spotless formatting checks, Maven tests with JaCoCo reports, frontend ESLint and Prettier checks, frontend type/build/tests, dependency review, and Trivy scans of both container images. CycloneDX SBOMs are uploaded with each image scan. Images publish only from pushes to `master`, after both scans pass. Gitleaks and Semgrep also run on a daily schedule.

Java formatting is applied with `./mvnw spotless:apply` and checked by `./mvnw spotless:check`. The current coverage baseline is 77.6% lines for the API and 42.9% for the worker; CI prevents either module from dropping below 75% or 40%, respectively. Raise those thresholds as test coverage grows.

To block staged secrets locally, install [pre-commit](https://pre-commit.com/) and run `pre-commit install` once. Run all configured hooks with `pre-commit run --all-files`. CI remains the enforcement point.
