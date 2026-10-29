# Operations

## Run

- `mvnw.cmd -o clean verify -B` — offline build + test (363 tests, 0 failures, 0 skipped).
- `docker compose up -d` — starts Kafka (KRaft, single node, `PLAINTEXT://localhost:9092`),
  Postgres 16, Redis 7.2.4. API runs under `prod` profile; `simulation-worker` under
  `worker` profile.
- To run a single load scenario: `loadtest/run-baseline.ps1` (3 measured runs per condition,
  median/min–max, TRUNCATE simulations before each run). See `docs/performance.md` §1–2 for
  method and caveats.

## Configure

- **Kafka:** `compose.yaml` — `apache/kafka:3.7.0` KRaft, advertised `PLAINTEXT://localhost:9092`,
  `SIMULATION_CLAIM_LEASE:12m`, `SIMULATION_CONSUMER_GROUP:probity-simulation-worker`.
- **DB / Redis:** connection strings in `probity-api/src/main/resources/application.yaml` and
  `probity-worker/src/main/resources/application.yaml` (environment overrides via compose).
- **Worker profile:** `probity.simulation.worker.enabled` defaults `true`; set `false` to run
  the consumer inside the API monolith. DB role `simulation_worker` gets `SELECT`/`UPDATE` on
  `simulations`, `SELECT` on read tables only, no `users` access. Role created out-of-band;
  migration `V18__worker_role_grants.sql` is guarded/idempotent.

## Deploy

- **Docker Desktop (local):** `docker compose up -d`; API on `:8080`, worker consumer on port 8081.
- **K8s:** `k8s/simulation-worker-deployment.yaml` — 2 replicas, `terminationGracePeriodSeconds: 660`,
  requests 500m/1Gi, limits 2/2Gi, port 8081. API is not manifested in this repo.
- CI: `Build & Push Docker Image` runs on push to `master`; required check `CI Status`.

## Observability

- **Prometheus:** scrape `compose.yaml` (Grafana 10.4.2, Loki 2.9.4 images available but not started).
- **Known gaps** (documented, not hidden):
  - No distributed tracing and no trace ID in logs.
  - No HTTP latency histograms (`_count`/`_sum`/`_max` only), no Kafka consumer-lag meter,
    and zero alerting rules.
  - Loki does not start with the current config; nothing ships logs to it.
  - No `_bucket` or `le` labels in any Prometheus rule; rule files are empty/absent.
  - No shipper configured; Loki `mkdir wal: permission denied` observed on first start.
  - Worker target down when running Docker Desktop behind corporate proxy; set no-proxy for
    `localhost` and `127.0.0.1`.