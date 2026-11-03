# PR-D2 — Extract the async simulation worker (`probity-worker`) (Phase 4)

> Draft PR description. Branch: `pr-d2-worker-module`. Nothing here is
> committed yet.

## Scope

PR-D2 extracts the async simulation machinery out of the API remainder into its
own deployable, **`probity-worker`**, per `docs/plans/PHASE_4_WORKER_EXTRACTION.md`
§2.2 (role grants), §2.6 (compose service) and §2.9 (ArchRule/lease invariant). It
is a *module split* — not a behavior change: concurrency stays at **1**, all
partitions / lease / poll / park / seek values are untouched, and the `@Disabled`
concurrency-placeholder test stays disabled (§2.7 is still a later-PR TODO). The
PR-D3 `@ConditionalOnProperty` feature gate is **not** part of this PR.

After the PR, the async flow runs in a separate process that shares the same
`me.veselin.probity` packages and the same Postgres schema, consumes from the live
Kafka topic, and owns a least-privilege database role the API's migrations grant.

## What it does

### Module split (`me.veselin.probity`, packages unchanged)

- Adds the **`probity-worker`** module (`probity-worker/pom.xml`): depends on
  `probity-shared`, brings `spring-kafka`/`spring-web`/`data-jpa`/`postgresql`
  plus testcontainers + ArchUnit; the **`api` now depends on `probity-worker`**
  (still scanning its jar under `me.veselin.probity` — both contexts see the same
  beans). The reactor is now `parent → shared → worker → api`.
- Moves the async simulation consumer machinery into the worker via `git mv`
  (packages/imports unchanged): the `SimulationAsync*` Kafka consumer,
  `SimulationSubmittingService`/`MarketDataSyncService`/portfolio & risk helpers,
  and the DTOs/ports they need (`AssetMetadataDto` → worker, `RestConfig` → worker
  so the `RestTemplate` bean the worker's `YahooFinanceAdapter` needs lives where
  it is consumed).
- Adds the **worker bootstrap**: `me.veselin.worker.WorkerApplication`
  (`scanBasePackages = me.veselin.probity` **plus explicit** `@EntityScan` and
  `@EnableJpaRepositories` over `me.veselin.probity` — auto-config packages are
  derived from `WorkerApplication`'s own location, not from `scanBasePackages`),
  `WorkerEventConfiguration`, the worker `application.yaml` (datasource from
  `DB_*` env, `server.port: ${WORKER_PORT:8081}`, `claim-lease:
  ${SIMULATION_CLAIM_LEASE:12m}`, consumer group
  `${SIMULATION_CONSUMER_GROUP:probity-simulation-worker}`, `ddl-auto: validate`,
  `flyway.enabled: false` — *the api owns the deployed schema*), and
  `Dockerfile.worker` (runs `-pl probity-worker -am`).

  Spring Data lesson, recorded: annotations on the application class are
  `scanBasePackages`-independent — `@EntityScan`/`@EnableJpaRepositories` had to be
  spelled out or the worker context found neither entities nor repositories.

### Async test slice moves with the code

- The 12-test async slice (9 async slice tests, the poison-payload prod-config
  tests, the duplicate-delivery/fresh-processing/partition-park/ broker-control
  integration tests, and the `@Disabled` placeholder) moves to
  `probity-worker/src/test/` with a lean `@ServiceConnection`-based
  `BaseAsyncSimulationIntegrationTest`.
- The worker test profile migrates the throwaway Postgres with **the same
  V1..V18 set** (copied into worker test resources so the shipped jar never
  carries migrations). This required `flyway-core` + `flyway-database-postgresql`
  as **test-scoped** deps in the worker pom — without them
  `FlywayAutoConfiguration` backs off and the test DB stays empty (`missing table
  [assets]` under `ddl-auto: validate`).
- The api keeps its slim async request test; `SimulationAsyncUnclaimableConcurrencyPlaceholderTest`
  remains `@Disabled`.

### V18 — least-privilege role grants (grants only)

`probity-api/src/main/resources/db/migration/V18__worker_role_grants.sql` grants
`probity_worker` (idempotently, guarded by `IF EXISTS` on the role — **it never
creates the role or a password**; provisioning the role is an ops step, see
Verification):

- `USAGE` on schema `public`
- `SELECT` on `simulations`, `portfolios`, `portfolio_positions`, `assets`,
  `price_bars`, `flyway_schema_history` (Hibernate `ddl-auto: validate` needs it)
- `UPDATE` on `simulations` (the worker's claimed-row updates)

No `INSERT`/`DELETE`, no sequence privileges. The migration is copied into worker
test resources only so the throwaway test DB exercises the identical set.

### Compose service + jar/plugin wiring

- `compose.yaml` gains a `simulation-worker` service (`profiles: [worker]`, no
  host port, `vesko49/probity-worker:latest`, `SPRING_DOCKER_COMPOSE_ENABLED=false`
  + `DB_*`/`KAFKA_BOOTSTRAP_SERVERS`/`SIMULATION_*` env, `depends_on` postgres +
  kafka `service_healthy`).
- The worker's Spring Boot repackage is published with an **`exec` classifier**:
  without it, `repackage` replaces the thin jar with the BOOT-INF-nested fat jar
  and the api's compile classpath ("package me.veselin.probity.* does not exist"
  across every moved package) finds nothing. Thin `probity-worker-*.jar` stays the
  main artifact for the api dependency; the runnable `*-exec.jar` is what
  `Dockerfile.worker` copies into the image.

## Tests

Moved, not rewritten: the async slice reports identical results in the worker, and
the api reports exactly baseline minus the moved slice.

Test arithmetic (surefire counts): **api 349 + worker 14 = 363 run, 1 skipped** —
the one skip is the `@Disabled` concurrency placeholder (§2.7). Baseline 361 was
split as **baseline 361 = 12 tests moved to the worker + 2 new ArchRule tests**.

- **api**: 349 run, 0 failures, 0 errors, 0 skipped (baseline 361 − 12 moved).
- **worker**: 14 counted (13 executed + 1 skipped) — 12 moved tests (11 active +
  the `@Disabled` placeholder) + 2 new `ArchitectureTest` rules (one asserts the
  lease/poll invariant, the other the partition-count/park constants).

## Build result

```
$ cmd /c "mvnw.cmd -o clean verify -B > build-out.txt 2> build-err.txt"
exit 0
[INFO] Building Probity 0.0.1-SNAPSHOT                            [1/4]
[INFO] Building Probity Shared 0.0.1-SNAPSHOT                     [2/4]
[INFO] Building Probity Worker 0.0.1-SNAPSHOT                     [3/4]
[INFO] Building Probity API 0.0.1-SNAPSHOT                        [4/4]
[INFO] BUILD SUCCESS                          (elapsed 04:01)
[WARNING] Tests run: 14, Failures: 0, Errors: 0, Skipped: 1      (worker)
Tests run: 349, Failures: 0, Errors: 0, Skipped: 0               (api)
```

Jars: `probity-worker/target/probity-worker-0.0.1-SNAPSHOT.jar` (thin, 173 KB — the
api's compile dependency) and `probity-worker-0.0.1-SNAPSHOT-exec.jar` (runnable fat
jar for Docker).

## Docker verification (live stack)

- `probity-postgres-1` / `probity-kafka-1` / `probity-redis-1` already up (89d
  stack) — postgres 5432, kafka 9092 reachable.
- `CREATE ROLE probity_worker LOGIN PASSWORD ...` in the live `probity` DB (ops
  step, never done by V18), then **V18 applied** → grants confirmed:
  `SELECT` on `assets`, `flyway_schema_history`, `portfolio_positions`,
  `portfolios`, `price_bars`, `simulations` + `UPDATE` on `simulations` — nothing
  more.
- `java -jar probity-worker-0.0.1-SNAPSHOT-exec.jar` with
  `DB_USERNAME=probity_worker` / `DB_HOST=localhost` / `DB_NAME=probity` /
  `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`:
  - HikariPool-1 connected as `probity_worker`; **no schema-validation errors**
    (the V18 grants satisfy `ddl-auto: validate`),
  - joined group `probity-simulation-worker`, got all **6 live partitions**
    assigned,
  - `Started WorkerApplication in 10.676 seconds`,
  - `GET /actuator/health` → **`{"status":"UP"}`** on port 8081.

### Images

- `probity-api:pr-d2` — builds clean (526 MB; 4-pom copy + full-reactor package).
- `probity-worker:pr-d2` — builds clean (Dockerfile.worker: all four poms copied
  before `dependency:go-offline` — the root aggregator references `probity-api`,
  so a worker-only pom copy made the reactor unparseable — then `-pl probity-worker
  -am` for both go-offline and package; run stage copies the `*-exec.jar`).

## Pre-push verification checks (master `945c4b4` vs branch)

These were run read-only before pushing; nothing was changed as a result.

- **(a) Consumer group — no drift.** The simulation listener's effective group id
  is **`probity-simulation-worker` on both master and this branch**. Master
  declares it in `probity-api/src/main/resources/application.yaml`
  (`probity.simulation.consumer-group: ${SIMULATION_CONSUMER_GROUP:probity-simulation-worker}`
  + `spring.kafka.consumer.group-id: probity-simulation-worker`); this branch
  carries the identical two properties in the **worker** yaml (the listener's
  `@KafkaListener(groupId = "${probity.simulation.consumer-group:probity-simulation-worker}")`)
  and the identical copies remain in the api yaml (vestigial — the api no longer
  declares any listener). `auto.offset.reset: earliest` and the poll/lease values
  are also byte-identical between master and branch.
- **(b) No double consumption.** `@KafkaListener` exists in **only one place**:
  `probity-worker/.../simulation/event/SimulationRequestedEventConsumer.java`
  (`id = "simulation-requested"`, `groupId` from the property above,
  `containerFactory = "kafkaListenerContainerFactory"`). `probity-api` main sources
  contain **zero** `@KafkaListener` and zero `KafkaListenerContainerFactory` beans;
  the only api hit is a test-helper reference to `KafkaListenerEndpointRegistry` in
  the slimmed api async base (no listener in the api context).
- **(c) V18 guard.** The full migration (39 lines) is a single idempotent
  `DO $$ BEGIN IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'probity_worker')
  THEN GRANT ... END IF; END $$;` block. **It never `CREATE`s the role and never
  sets a password** (Q7: role provisioning is out of band); when the role is absent
  the block is a no-op and startup is not blocked. CI/Testcontainers start from a
  DB without the role and the migration applies cleanly — proven by the offline
  `verify` run, where both the api suite and the worker suite migrate fresh
  containers with the same V1..V18 set (V18 copied into worker test resources).
- **(d) Compose.** `compose.yaml` **does** have the worker service:
  `simulation-worker` (image `vesko49/probity-worker:latest`, `profiles: [worker]`,
  no host port, `SPRING_DOCKER_COMPOSE_ENABLED=false`, `DB_*`/`KAFKA_*`/
  `SIMULATION_CONSUMER_GROUP` env, `depends_on` postgres + kafka `service_healthy`).
- **(f) Git status.** `git status --short` shows **only** the untracked
  `docs/plans/PR-D0_DESCRIPTION.md`, `PR-D1_DESCRIPTION.md` and
  `PR-D2_DESCRIPTION.md`. The `build-*.txt`/`app-*.txt`/`k6-*.txt` artifacts in the
  repo root are gitignored (`build-out.txt`, `app-out.txt`, `k6-out.txt`,
  `k6-baseline-c1/c6.txt` — all `check-ignore` = true) and `graphify-out/` is
  gitignored, so none appear in the status.

## Commit list (`master..HEAD`, 11) — every non-move change

1. `4f735f0` — **new `probity-worker/pom.xml` (+153)**, `probity-api/pom.xml` +9
   (adds the `probity-worker` dependency), root `pom.xml` +1 (module). The other 67
   files are pure `git mv` (0 changes).
2. `087ec99` — **new files**: `WorkerApplication.java` (+75),
   `WorkerEventConfiguration.java` (+55), worker `application.yaml` (+104),
   `Dockerfile.worker` (+26); `Dockerfile` +2 (adds the worker source + pom to the
   api build context). No existing source edited.
3. `8c2dbc1` — pure `git mv` of `AssetMetadataDto.java` (0 changes).
4. `501c062` — test-slice move **with edits**: api `AsyncKafkaTestSupport` and
   `BaseAsyncSimulationIntegrationTest` slimmed for the listener-less api context;
   worker gains the lean `@ServiceConnection` base (+425) and `AsyncKafkaTestSupport`
   (+236), `application-test.yaml` (+40) and the V1..V17 migration copies (18
   files; V18 arrives in 0709a28). The 9 async test classes +
   `UntrustedSimulationPayload` are pure moves (0 changes).
5. `ecd5aec` — **new `ArchitectureTest.java` (+51)** — lease/poll invariant +
   partition/park constants.
6. `da3dd3e` — **`compose.yaml` +26** — `simulation-worker` service (profile `worker`).
7. `0709a28` — **new migration `V18__worker_role_grants.sql`** (+39), identical copy
   into worker test resources (+39) so the test DB exercises the same set.
8. `4930886` — pure `git mv` of `RestConfig.java` (0 changes).
9. `5f9bbbe` — **`WorkerApplication.java` +17/−4** (`@EntityScan`/
   `@EnableJpaRepositories` + javadoc correction), `probity-worker/pom.xml` +18
   (test-scoped `flyway-core` + `flyway-database-postgresql`).
10. `620b2d2` — **`probity-worker/pom.xml` +9** (repackage `exec` classifier keeps
    the thin jar for the api compile), `Dockerfile.worker` ±1 (copy the `-exec.jar`
    by name).
11. `c1da97f` — **`Dockerfile.worker` ±2** (copy `probity-api/pom.xml` so the
    reactor parses; scope `dependency:go-offline` to `-pl probity-worker -am`).

> **Review tip:** pure-move commits (`4f735f0`'s 67 files, `8c2dbc1`, `4930886`,
> the 9 moved test classes) are best viewed with rename detection (`git diff -M`).
> All functional edits are listed above and confined to bootstrap, module wiring,
> migrations, compose, and the Dockerfile fixes.

> **Merge with a merge commit or rebase-and-merge — not squash** — so the pure
> `git mv` history and the standalone wiring-fix commits stay visible (same reason
> as PR-D1).

## Not in scope (recorded, not changed)

- Concurrency, partitions, lease/poll, park/seek values — untouched (STILL PR-D0's
  concurrency 1, 6 partitions, 12 m claim lease, 10 m `max.poll.interval.ms`).
- PR-D3's `@ConditionalOnProperty` gate and the worker/api feature-flag split.
- `@Disabled` concurrency-placeholder test (§2.7) — stays disabled.
- Seed/base-test asset ids, partition-count assertions, k6 re-run — unchanged;
  `docs/PHASE_4_MEASUREMENTS.md` untouched.
- The api's `SpringApplication`/executors keep working with the worker jar on the
  classpath (verified — the api suite is green post-split).

## Follow-ups (not in this PR)

- Add a `.dockerignore` (build context currently ships local `target/`, `.git`,
  `graphify-out/` — 131 MB at PR-D1).
- Fix stale jar paths in the root `README.md` and `loadtest/README.md` (they still
  reference the old single-module `target/Probity-0.0.1-SNAPSHOT.jar`), and refresh
  `loadtest/` + `docs/PHASE_4_MEASUREMENTS.md`.
- Promote the behind-profile `simulation-worker` compose service (currently
  `profiles: [worker]`) into default operation once the worker deploys, incl. the
  CPU HPA recheck for it.
- Re-run the k6 baseline **against the worker** (the async path now runs in the
  worker process, not the api).

## Environment notes

- The full reactor was previously *never* green end-to-end after the split, but is
  now: each attempt surfaced one masked wiring defect (RestTemplate bean location →
  JPA scan → missing Flyway → fat-jar repackage → Dockerfile go-offline reactor
  parse). All five are fixed above and re-verified in one clean `verify`.
- The live `probity_worker` role/password were created manually per the docs; V18
  deliberately does not provision them.