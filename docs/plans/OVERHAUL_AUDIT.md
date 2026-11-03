# Documentation + Observability Overhaul — Part 1 Audit

Untracked scratch. Not committed. Read-only audit of `a9eb858` (`docs-q9c-decision`) plus a
live run of the built API jar and the `monitoring/` stack. Nothing was deleted, moved,
rewritten or merged.

Every claim below is either a `file:line` citation or a command result recorded in this
session. Anything I could not verify says **not verified**.

## 0. Method and limits

- Inventory: `git ls-files '*.md'`, file sizes, line counts, headings.
- Accuracy: cross-checked prose claims against `pom.xml`, `frontend/package.json`,
  `Application.java`, migrations, surefire XML, and `docs/PHASE_4_MEASUREMENTS.md`.
- OpenAPI: started `probity-api/target/probity-api-0.0.1-SNAPSHOT.jar` (pid 49584) on
  :8080, fetched `GET /api-docs` → **200, 43,070 bytes**, then inspected the JSON.
- Observability: `docker compose up -d loki prometheus grafana`, queried Prometheus
  `/api/v1/targets`, `/api/v1/rules`, `/api/v1/status/flags`, Grafana `/api/health`,
  `/api/datasources`, `/api/search`, and scraped `:8080/actuator/prometheus`.
- Not verified: worker runtime metrics (worker never started; Prometheus target
  `simulation-worker:8081` = `no such host`), worker log output, and any Loki query
  (Loki is crash-looping, §6.3).

## 1. Inventory

**20 tracked markdown files, ≈504 KB. Plus 11 untracked `docs/plans/*` scratch files,
≈110 KB. Total ≈614 KB of prose for a 3-module backend + 1 frontend.**

| File | KB | Lines | What it actually is | Verdict |
|---|---|---|---|---|
| `README.md` | 36.3 | 596 | Pitch + architecture + benchmarks + quickstart + API table + monorepo + CI | **CONDENSE** (biggest single win) |
| `docs/TECHNICAL_REPORT.md` | 98.9 | 913 | Portfolio-style design report: claims, benchmarks, phases | **CONDENSE → `docs/architecture.md`** |
| `docs/PHASE_4_MEASUREMENTS.md` | 83.0 | 1113 | Actual measured results, method, caveats | **KEEP** (authoritative evidence) |
| `frontend/docs/GLOBAL_BEHAVIOR_RULES.md` | 44.7 | 1260 | Frontend UI contract | **MOVE** to `frontend/docs/` only copy |
| `docs/plans/MONOREPO_MIGRATION.md` | 39.2 | 381 | Executed migration plan | **DROP** (history) |
| `docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md` | 34.8 | 406 | Design history for Q9a/Q9b | **MOVE → short ADR** |
| `frontend/src/docs/GLOBAL_BEHAVIOR_RULES.md` | 30.7 | 868 | Near-duplicate of the file above | **DROP** (duplicate) |
| `docs/plans/PHASE_4_WORKER_EXTRACTION.md` | 29.1 | 202 | Executed extraction plan | **MOVE → short ADR** |
| `frontend/src/docs/COMPONENT_AUDIT_CHECKLIST.md` | 20.8 | 659 | One-off audit checklist | **DROP** |
| `docs/AGENT_LESSONS.md` | 18.5 | 230 | Delivery-process notes for AI agents | **MOVE** out of `docs/` |
| `frontend/src/docs/IMPLEMENTATION_GUIDE.md` | 17.1 | 567 | Frontend how-to | **KEEP** (frontend scope) |
| `docs/PHASE_2_IMPLEMENTATION_NOTES.md` | 12.0 | 172 | Historical phase notes | **DROP** |
| `frontend/src/docs/state-aware-components.md` | 10.0 | 317 | Frontend component contract | **KEEP** (frontend scope) |
| `frontend/README.md` | 8.3 | 130 | Frontend quickstart | **KEEP** |
| `docs/plans/Q9C_DECISION.md` | 7.7 | 117 | Closed inconclusive investigation | **MOVE → ADR** |
| `docs/AGENT_PROTOCOL.md` | 6.3 | 84 | Agent working agreement | **MOVE → `CONTRIBUTING.md` / `.github/`** |
| `loadtest/README.md` | 6.2 | 95 | Harness usage | **KEEP** |
| `k8s/README.md` | 6.1 | 100 | Manifests usage | **KEEP** |
| `frontend/FRONTEND_BUILD_NOTES.md` | 5.4 | 99 | Build notes | **CONDENSE** into frontend README |
| `docs/plans/RENAME_CHECKLIST.md` | 0.6 | 11 | Repo rename `probity-backend`→`probity`, **not executed** | **DROP** or execute |

Untracked scratch (leave alone in Part 1; recommend `docs/plans/` → `docs/archive/` or
delete at will, git history is not even needed since they were never committed):
`FIX_COMPOSE_HEALTHCHECK_DOCKERIGNORE_DESCRIPTION.md`, `MONEREPO_MIGRATION.md` (note the
typo, duplicate of the tracked `MONOREPO_MIGRATION.md`), `PR-D0..D5_DESCRIPTION.md`,
`PR-Q9a_DESCRIPTION.md`, `PR-Q9b_DESCRIPTION.md`,
`WRITE_PATH_INVESTIGATION_DESCRIPTION.md`.

**Reader-value rule applied:** anything that exists only to narrate *how the work was
delivered* (PR descriptions, phase notes, agent protocols, migration/rename checklists)
is not documentation for an external engineer. Dropping it costs nothing because the
decisions that mattered belong in a 1-page ADR, and the rest is recoverable from git.

## 2. Proposed target layout

```
README.md                     # what/why, quickstart, stack, doc index — ≤150 lines
docs/architecture.md          # modules, request/Kafka flow, data model, package map
docs/performance.md           # measured results + method + caveats (from PHASE_4_MEASUREMENTS)
docs/operations.md            # compose, k8s, metrics/targets, health, known gaps
docs/decisions/               # 4–6 short ADRs (0001-...)
CONTRIBUTING.md               # build/test/style/agent workflow (absorbs AGENT_PROTOCOL)
loadtest/README.md, k8s/README.md, frontend/*   # unchanged scope
```

Mapping: `TECHNICAL_REPORT.md` → `architecture.md` (claims re-verified, benchmarks
deleted or replaced by measured numbers); `PHASE_4_MEASUREMENTS.md` →
`docs/performance.md` (keep method + caveats verbatim, it is the only trustworthy perf
source); `AGENT_PROTOCOL.md` + `AGENT_LESSONS.md` → `CONTRIBUTING.md` §"Working with
agents"; `Q9_RESULT_PAYLOAD_STORAGE.md`, `PHASE_4_WORKER_EXTRACTION.md`,
`Q9C_DECISION.md` → ADRs 0002–0004.

## 3. Content accuracy — red flags

| # | Claim | Where | Reality |
|---|---|---|---|
| A1 | "sub-second simulation results for 10,000+ path" | `README.md:3`, `docs/TECHNICAL_REPORT.md:11,15,417,956` | **Contradicted by own measurements.** e2e is 647–4,599 ms (`docs/PHASE_4_MEASUREMENTS.md` Scenario B trace §133-159, §874-1007). Delete or restate as "end-to-end seconds, compute-bound". |
| A2 | Benchmark table: 2,450 / 620 / 320 / 180 ms, 2.4 MB, 0 GC | `README.md:90-99`, `docs/TECHNICAL_REPORT.md:454-467` | **No harness, no commit, no record anywhere in the repo.** Unsourced. Must be deleted or re-measured with a committed harness. |
| A3 | Redis cache hit-rate table 85/92/78 % with hit/miss latency | `README.md:101-107`, `docs/TECHNICAL_REPORT.md:472-476,826-828` | **Impossible as written**: no `@Cacheable`, no `CacheManager`, no `@EnableCaching` in the codebase. Redis is used only for refresh/email/chat-memory/rate-limit/idempotency (`IdempotencyFilter`, `RedisChatMemoryRepository`). Delete. |
| A4 | "Structured JSON logging for Loki", "Correlation IDs", "Redis metrics", "custom simulation/cache metrics" | `docs/TECHNICAL_REPORT.md:336-345` | All false today: default Spring text log pattern, no MDC/traceId (`application.yaml` has no `logging.pattern`), no Redis/Lettuce meters exported, only custom meter is `probity_simulation_dlt_publish_failures_total`. |
| A5 | Project structure = single module, `application.properties`, "V1..V9 currently" | `README.md:182-206` | Stale: 3 Maven modules (`pom.xml:23-27`), `application.yaml`, migrations `V1..V19` (`V19__simulation_paths.sql` exists). |
| A6 | Test counts | `README.md:109` correct (388 run / 1 skipped); `docs/TECHNICAL_REPORT.md:14,635,640,958` say 354/354 | Surefire XML: api 363 tests / 33 files, worker 25 / 13 files, 1 skipped = **388**. TR numbers are stale by two Q9 rounds. |
| A7 | `/paths` and `/status` endpoints, async contract | README endpoint tables | Missing/partial: `GET /api/v1/simulations/{id}/status` and `/paths` exist in `SimulationController` but are under-described vs the 39 live operations. |
| A8 | Frontend package identity | `frontend/package.json:2` | Still `"@figma/my-make-file"`. Fine for an internal note, wrong as a public artifact. |
| A9 | Duplicate frontend rulebooks | `frontend/docs/GLOBAL_BEHAVIOR_RULES.md` (44.7 KB) vs `frontend/src/docs/GLOBAL_BEHAVIOR_RULES.md` (30.7 KB) | Two divergent copies of the same contract; readers cannot tell which is authoritative. |

What **is** trustworthy and should survive: `docs/PHASE_4_MEASUREMENTS.md` §31-73
(topology, what each metric measures), §336-432 (replica scaling), §434-484 (WAL),
§486-556 (rolling restart), §874-1159 (write vs response, Q9a), §1161-1295 (Q9b),
§1297-1313 (caveats: single 32-core laptop, 200 ms poll granularity, `server_latency`
excludes the completion UPDATE, ≈7.89 MB per completed row). Those caveats must move
verbatim; without them the numbers overclaim again.

## 4. OpenAPI — live spec gaps (31 paths / 39 operations / 49 schemas)

1. **Error responses point at the success DTO.** 68 of 87 `4xx/5xx` responses declare
   content, and it is always the happy-path schema — e.g. `POST /simulations/run` 400
   → `SimulationResponse`. Real body is the `Map.of(...)` envelope
   `{status,error,fields?,path,timestamp}` from `GlobalExceptionHandler.java:51-57`
   and 12 more handlers. This is the single most misleading thing in the spec.
2. **No error component at all.** `components.schemas` has no `ProblemDetail`/`ApiError`;
   the envelope is undocumented. Media type is `*/*` everywhere, not `application/json`.
3. **Auth docs are wrong for the public endpoints.** Top-level
   `security: [{"cookieAuth": []}]` applies globally, so `/auth/login`, `/auth/register`,
   `/auth/csrf` look like they need a cookie. `@SecurityRequirement` count = 0. No CSRF
   header scheme, even though `SecurityConfiguration` enforces CSRF outside the 4
   ignored paths.
4. **Constraints missing where they matter.** `n` (paths sample size) on
   `GET /simulations/{id}/paths` has no `minimum`/`maximum`/`default`, though
   `RunSimulationRequest` documents 100–10 000 and 1–1260 via `@Schema`/`@Min`/`@Max`.
   `Idempotency-Key` is `required: true` and only on 4 of the operations that the
   `IdempotencyFilter` actually guards.
5. **No description text and no 2xx bodies on 11 operations.** 39/39 operations have a
   summary but 0 have a `description`; 11 success responses declare no body at all
   (mostly 204, but `Location`/`ETag` semantics on `202`/`200` are only partially
   described). Duplicate `Portfolios` tag appears in the tag list.
6. **No committed spec, no drift test.** Nothing in `src/test` references `api-docs`,
   `OpenAPI` or `swagger`, so the spec cannot regress loudly. Add a
   `springdoc.api-docs.enabled` + snapshot test (or a `@SpringBootTest` asserting the
   error component and `Idempotency-Key` placement).

Annotations already present: `@Operation` 39, `@ApiResponse(s)` 209, `@Schema` 29,
`@Parameter` 5, `@Tag` 7 — the work is fixing *what* they say, not adding from zero.

## 5. Observability — verified state

Metrics (`:8080/actuator/prometheus`, live):
- Present: JVM, HikariCP (incl. acquire/usage timers), Tomcat, executor pools,
  Spring Security, `spring_kafka_template_*`, `application_started/ready_time`.
- **`http_server_requests_seconds` exports only `_count`, `_sum`, `_max`. There are no
  `_bucket` series and no `percentiles-histogram`/`percentiles` config, so p95/p99 are
  not computable from Prometheus.** Either add
  `management.metrics.distribution.percentiles-histogram.http.server.requests: true`
  or accept `_max` only. Right now the README/report imply latency percentiles exist.
- **No Redis/Lettuce meters** are exported despite the README claiming them.
- **Only one custom business meter**: `probity_simulation_dlt_publish_failures_total`
  (`SimulationFailedRecordRecoverer.java:33`). No simulation duration, no queue depth,
  no idempotency hits.
- **No Kafka consumer-lag metric** (`KafkaClientMetrics` / `records.lag` absent), so the
  one number an operator needs for the async pipeline is unavailable.
- `uri` label has 4 bounded values → no cardinality problem.

Scrape/config (`monitoring/`):
- `monitoring/prometheus.yaml`: 15 s scrape; `probity` target **UP**;
  `probity-worker` target **DOWN** — `lookup simulation-worker on 127.0.0.11:53: no such host`
  (worker not in the compose network / not running).
- `retention.time = 0s`, `retention.size = 0B` → silently using the 15 d default; not a
  decision anyone made on purpose.
- **0 rule groups → zero alerts.** A dead worker target is invisible.
- Grafana 10.4.2 healthy; Prometheus datasource health **OK**; Loki datasource health
  **400**; `GET /api/search?type=dash-db` → **empty** (no provisioned dashboards).
- Loki 2.9.4 is in a restart loop:
  `creating WAL folder at "/wal": mkdir wal: permission denied` →
  `error initialising module: ingester` (`compose.yaml:67-80`, `monitoring/loki-config.yaml`
  has no `common.path_prefix`/`ingester.wal` dir and the bind channel does not cover
  `/wal`). Also **no Promtail / no log shipper anywhere**, so even a healthy Loki would
  receive nothing.
- Health: API exposes `health,prometheus` with `show-details: never`
  (`application.yaml:186-193`); worker adds `probes.enabled: true`. Fine for k8s probes,
  useless for humans.

Logs: default Spring text pattern, no `traceId`/`spanId`/correlation ID, no MDC filter,
no `logging.pattern.*` override in either `application.yaml`. `/actuator/prometheus` is
`permitAll` (`SecurityConfiguration`), which is fine locally and should be called out as
a deployment decision rather than left implicit.

### OTel recommendation (evidence-based, minimal)

Do **not** replace Micrometer/Prometheus. The gap is (a) no percentile buckets, (b) no
Kafka lag, (c) no way to follow one simulation from the API request through Kafka into
the worker, and (d) logs that cannot be joined to anything.

Recommended shape:
1. Keep Micrometer + Prometheus as the metrics store; add
   `percentiles-histogram` for `http.server.requests` and
   `spring.kafka.listener`/consumer lag via `KafkaClientMetrics`.
2. Add `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` to `probity-api`
   and `probity-worker`, with W3C propagation over Kafka headers. That is the only way
   to answer "which worker handled simulation X and how long did each hop take".
3. Add `logging.pattern.level` including `%X{traceId}`/`%X{spanId}` so the existing text
   logs become joinable, and ship them with Grafana Alloy (or Promtail) once Loki is
   fixed. No JSON log encoder needed to get value; traceId in the pattern is enough.
4. Do not add a tracing backend in this PR. OTLP → an existing collector/Jaeger/Tempo is
   a separate decision; without a backend, steps 1–3 still pay off (trace IDs in logs,
   histograms, lag).

Estimated size: **PR-M1** metrics/alerts (~1 day: histogram + lag + 4 alert rules +
one Grafana dashboard + fix Loki `path_prefix` + add shipper), **PR-M2** tracing
(~1–2 days: 2 poms, 2 yaml blocks, Kafka observation, MDC pattern, one integration test).

Smallest first step with real value: **one Prometheus alert rule plus the
percentiles-histogram flag**. That turns the already-broken worker target into a page
and makes the latency claims measurable — no new dependency, no new service.

## 6. Part 2 shape (not started, for approval only)

- **PR-DOC-1** — rewrite `README.md` + `docs/architecture.md`, delete A1–A5 claims,
  move measurements to `docs/performance.md`, add doc index. No code changes.
- **PR-DOC-2** — removals/relocations: drop executed plans, duplicate frontend rules,
  phase notes; add 4 short ADRs; `AGENT_PROTOCOL`→`CONTRIBUTING.md`.
- **PR-API-1** — OpenAPI: `ApiError` component, correct error refs, per-operation
  `security`, CSRF scheme, param constraints, `application/json` media type, committed
  spec snapshot + drift test.
- **PR-M1** — observability: histograms, Kafka lag, alert rules, dashboard, Loki fix,
  log shipper.
- **PR-M2** (optional, needs approval) — tracing as in §5.

## 7. Questions for the owner

1. **Scope of "external reader":** is this a hiring-portfolio artifact (then the
   benchmark claims matter and A2 must be re-measured) or an onboarding doc for
   contributors (then A2 can simply be deleted)? This changes PR-DOC-1 materially.
2. **Benchmarks:** do you want a committed, reproducible harness (k6/JMH) so
   `docs/performance.md` has numbers you can defend, or is `PHASE_4_MEASUREMENTS.md`
   alone enough?
3. **Dropping history:** confirm that deleting `TECHNICAL_REPORT.md`'s report-style
   sections, `PHASE_2_*`, executed plans, and `AGENT_*` from `docs/` is acceptable, with
   git history as the archive. Yes/no per group.
4. **Tracing backend:** is there an existing OTLP collector/Tempo/Jaeger you want to
   target, or should PR-M2 stop at trace IDs in logs + OTLP export config?
5. **Repo rename** (`probity-backend` → `probity`): execute it in this overhaul or drop
   `RENAME_CHECKLIST.md`? It affects every absolute URL in the docs.
6. **Part 1 side effects:** ok to leave the audit-started processes running for now —
   API jar pid 49584 on :8080, compose `loki`/`prometheus`/`grafana` — or should I stop
   them at the end of this step?

---

**Verdict:** the repo has one genuinely strong asset (`PHASE_4_MEASUREMENTS.md`) buried
under ≈600 KB of prose whose headline claims are contradicted by that very file. Fixing
accuracy is worth more than adding volume. Observability currently cannot answer
"is the worker alive" (no alert), "how slow is p95" (no buckets), or "which hop failed"
(no trace ID) — three small, independent fixes.
