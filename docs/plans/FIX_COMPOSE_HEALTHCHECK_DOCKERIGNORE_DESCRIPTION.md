# Fix: compose Kafka healthcheck, add .dockerignore

Post-PR-D4 hygiene fix. No `src/`, test or pom change.

## 1. Kafka healthcheck (`compose.yaml`)

`apache/kafka:3.7.0` ships the tool only as `/opt/kafka/bin/kafka-broker-api-versions.sh`;
that directory is **not** on `PATH`
(`PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin`),
so the bare `kafka-broker-api-versions` never resolved and the broker reported
`unhealthy`. Both `app` and `simulation-worker` wait on it via
`depends_on: ... service_healthy`, so they could never start.

Evidence:
- `command -v kafka-broker-api-versions` → `PLAIN_NOT_ON_PATH`
- `command -v kafka-broker-api-versions.sh` → `SH_NOT_ON_PATH`
- `/opt/kafka/bin/kafka-broker-api-versions.sh --help` → exit 0

Fix (one line): call the full path. Nothing else in `compose.yaml` changed.

Verified from a clean state: `docker compose down kafka` (volume kept) → `up -d kafka`
→ `docker compose ps kafka` = **healthy after ~15 s**; `docker compose --profile worker up -d`
returned in **12.8 s** (`kafka Waiting → Healthy → simulation-worker Started`, no hang),
then the worker was stopped. Postgres data intact (23 simulations).

## 2. `.dockerignore` (new, repo root)

Excludes `.git`, `**/target`, `graphify-out`, `docs`, `loadtest`, `k8s`, `*.md`,
`build-*.txt`, `app-*.txt`, `k6-*.txt`, `.idea`, `.vscode`, `node_modules`; keeps every
path the Dockerfiles `COPY` (root + module `pom.xml`s, `probity-*/src`, `.mvn`, `mvnw`).

Both images build (exit 0); Docker-reported context **253.5 MB → 651.49 kB** (api) /
**→ 18.09 kB** (worker); the PR-D1 figure was 131 MB.

## 3. Verification

`mvnw -o clean verify -B` exit 0 / BUILD SUCCESS (2:47), **365 run / 1 skipped**
(350 api + 15 worker) — unchanged.
