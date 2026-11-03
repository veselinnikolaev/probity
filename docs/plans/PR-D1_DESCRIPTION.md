# PR-D1 — Shared kernel + multi-module restructure (Phase 4)

> Draft PR description. Branch: `pr-d1-shared-kernel`. Nothing here is
> committed yet.

## Scope

PR-D1 restructures the single-module app into a **multi-module reactor** without
changing any behavior, per `docs/plans/PHASE_4_WORKER_EXTRACTION.md` §2.9. It splits
the codebase into a shared kernel (`probity-shared`) and the API remainder
(`probity-api`) so the simulation worker can later be extracted as its own deployable
against the same kernel. It deliberately keeps concurrency at **1**, all partitions /
lease / poll / park / seek values untouched, and does **not** enable the `@Disabled`
concurrency-placeholder test (that is §2.7, still a TODO for a later PR). It does
**not** touch the k6 baseline or any measurements.

Concretely, this PR:

- Adds a **root aggregator `pom.xml`** (`<modules>`: `probity-shared`, `probity-api`)
  that keeps `spring-boot-starter-parent`, the `spring-ai` BOM and
  `java.version`/`spring-ai.version` properties.
- Adds **`probity-shared/pom.xml`** — shared kernel with just
  `spring-boot-starter` + `spring-boot-starter-data-jpa` + `lombok`.
- Moves the **shared kernel** into `probity-shared` (13 files, `git mv`, packages
  unchanged): the simulation aggregate/persistence slice plus the domain-event marker
  and audit base classes it depends on (see below).
- Moves **all remaining `src/main` + `src/test`** under **`probity-api/`** (`git mv`,
  packages unchanged) and adds **`probity-api/pom.xml`** with every dependency from
  the old root pom plus `me.veselin:probity-shared`, and the Spring Boot plugin.
- **No code edits anywhere.** Because `me.veselin.probity.*` packages are unchanged,
  the `probity-api → probity-shared` Maven dependency resolves every existing import
  unchanged, and the `@SpringBootApplication`/JPA/ArchUnit scans (root package
  `me.veselin.probity`) still cover the shared jar on the test classpath.

### Scope clarification (decided with the reviewer, before work)

The §2.9 move list as literally written cannot compile: the `Simulation*Event`
records `implements DomainEvent`, and `Simulation` + `SimulationJpaEntity`
`extends BaseEntitySoftDelete extends BaseEntity` — all three classes staying in
`probity-api` would force `probity-shared → probity-api`, a circular Maven
dependency. Two decisions were confirmed up front:

1. **Dependency closure moves with the kernel** — `DomainEvent`,
   `BaseEntity`, `BaseEntitySoftDelete` are also `git mv`-ed into `probity-shared`
   (same packages), keeping the split self-contained with **zero import/package edits**.
2. **Moves-forced path fixes** — the `Dockerfile` (root `src/` no longer exists;
   `COPY src ./src` → copy both module dirs, jar copied from
   `probity-api/target/*.jar`) and CI surefire-report path
   (`target/surefire-reports/` → `probity-api/target/surefire-reports/`) were updated
   so the branch stays buildable per §2.9's "independently shippable and reversible".

### Files

| File | Change |
| --- | --- |
| `pom.xml` | rewritten as root **aggregator** (`packaging=pom`, `<modules>`: `probity-shared`, `probity-api`) — parent, properties, spring-ai BOM kept; all app dependencies removed |
| `probity-shared/pom.xml` | **new** — shared-kernel module (starter, starter-data-jpa, lombok) |
| `probity-api/pom.xml` | **new** — API module: old root dependencies verbatim + `me.veselin:probity-shared`, Boot plugin for the fat jar |
| `probity-shared/src/main/java/me/veselin/probity/common/domain/event/{DomainEvent,SimulationRequestedEvent,SimulationCompletedEvent,SimulationFailedEvent}.java` | `git mv` from `src/main/...` |
| `probity-shared/src/main/java/me/veselin/probity/common/audit/{BaseEntity,BaseEntitySoftDelete}.java` | `git mv` from `src/main/...` |
| `probity-shared/src/main/java/me/veselin/probity/simulation/domain/{Simulation,SimulationPayload,SimulationStatus}.java` | `git mv` from `src/main/...` |
| `probity-shared/src/main/java/me/veselin/probity/simulation/persistence/{SimulationJpaEntity,SimulationJpaRepository,SimulationMapper,SimulationRepository}.java` | `git mv` from `src/main/...` |
| `probity-api/src/main/**` (remaining sources + `resources/` incl. `application.yaml`, `db/migration/*`) | `git mv` from `src/main/...` |
| `probity-api/src/test/**` (all tests + `application-test.yaml`) | `git mv` from `src/test/...` |
| `Dockerfile` | move-forced: copy `pom.xml` + both module poms separately, copy module dirs, jar from `probity-api/target/*.jar`; a follow-up commit splits the pom `COPY` (multi-source `COPY pom.xml probity-api/pom.xml probity-shared/pom.xml ./` flattens all three to `/app/pom.xml`) into three single-source lines so `mvn dependency:go-offline` can resolve the reactor |
| `.github/workflows/ci_cd.yaml` | move-forced: surefire artifact path → `probity-api/target/surefire-reports/` |

> **Review tip:** view commit `b826442` with rename detection — `git diff -M
> b826442^ b826442` or GitHub's "Files changed" (rename view). Every moved file
> shows **0 changes**. The three commits: `b826442` (pure `git mv`, nothing else),
> `46a2ecf` (two new poms + three move-forced config path fixes), and
> `ee1c149` (Dockerfile pom `COPY` split — see table above).

Total: 258 renames (100 % similarity, zero content changes in moved files), 2 new
poms, 4 config-path fixes. `git diff --stat -M` shows every moved file at `| 0`.

> **Merge suggestion:** use a **merge commit or rebase-and-merge — not squash** — so
> the pure `git mv` commit (`b826442`) stays visible and the rename similarity
> (100 %) survives in history. Squashing would also fold in the standalone
> Dockerfile-fix commit.

## Tests

**No test code changed, added, or removed.** All 361 tests `git mv`-ed unchanged;
the `@Disabled` PR-D1 placeholder (`SimulationAsyncUnclaimableConcurrencyPlaceholderTest`)
remains disabled per §2.7.

## Build result

```
$ cmd /c "mvnw.cmd -o clean verify -B > build-out.txt 2> build-err.txt"
exit 0
[INFO] Reactor Summary for Probity 0.0.1-SNAPSHOT:
[INFO] Probity ............................................ SUCCESS [  0.148 s]
[INFO] Probity Shared ..................................... SUCCESS [  2.935 s]
[INFO] Probity API ........................................ SUCCESS [03:43 min]
[INFO] BUILD SUCCESS
[INFO] Total time:  03:47 min
[WARNING] Tests run: 361, Failures: 0, Errors: 0, Skipped: 1
```

The one skipped test is the `@Disabled` concurrency placeholder (§2.7 TODO — not
enabled by PR-D1). Jars: `probity-api/target/probity-api-0.0.1-SNAPSHOT.jar` (fat)
and `probity-shared/target/probity-shared-0.0.1-SNAPSHOT.jar`.

## Not in scope (recorded, not changed)

- Concurrency, partitions, lease/poll, park/seek values — untouched (still PR-D0's
  concurrency 1, 6 partitions, 12 m claim lease, 10 m `max.poll.interval.ms`).
- k6 baseline and `docs/PHASE_4_MEASUREMENTS.md` — no re-run; PR-D1 is a pure move.
- `@Disabled` concurrency-placeholder test (§2.7) — stays disabled.
- Q4 CPU-HPA recheck, Q9 payload-storage change, `pg_stat_wal.wal_bytes` follow-up,
  and Q5/Q7/Q8 open items — design-note entries only.
- `loadtest/README.md` (references the old `target/Probity-0.0.1-SNAPSHOT.jar` jar
  path) and the root `README.md` source tree — intentionally stale until the
  docs/measurements PR.
- `compose.yaml` / worker image / HPA — PR-D2+.

## Follow-up

- **Add a `.dockerignore`**: the build context was **131 MB** (no `.dockerignore`
  today — it ships local `target/`, `.git`, `graphify-out/`). Deferred to keep this
  PR strictly a move; cheap to add in the docs/measurements follow-up PR.

## Environment notes

- The first build attempt failed at the root `clean` step because a leftover
  `java -jar target/Probity-0.0.1-SNAPSHOT.jar` process (from before the
  restructure) locked the old root `target/` jar. That leftover process was stopped;
  the rerun was green. No relation to the restructure itself.