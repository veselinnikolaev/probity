# ADR 0002 — Extract the async simulation worker into its own deployable unit

**Status:** accepted and implemented (PR-D0..PR-D5, PR-Q9a, PR-Q9b merged).
**Supersedes:** the single-JVM listener layout.

## Context

Before the split, the same JVM both served HTTP and consumed `simulation-requested`. The
`202 Accepted` path was fast, but the completion write (≈ 4.27 MB raw / 1.47 MB stored
payload per simulation) made the consumer the throughput bottleneck: six concurrent
clients plateaued at **1.562 / 1.606 sims/s**, and `server_latency_ms` p95 rose from
≈ 34–50 ms single-flight to ≈ 3.9–4.1 s at concurrency 6 while `accept_latency_ms` stayed
in the tens of milliseconds (`docs/performance.md` §3).

Two constraints shaped the design: the claim/complete fence relies on one transactional
boundary in one database, and the portfolio read needs tables the worker does not own.

## Decision

Move execution into `probity-worker`, a separate Spring Boot application with its own jar
and image, and keep everything else in `probity-api`.

- **Q1 — data access: accept the read-only schema coupling now (Option B).** The worker
  reads `portfolios`/`positions`/`assets` (and `price_bars` when return/volatility are
  not overridden) and writes only `simulations`. Payload enrichment (resolved position
  snapshot) is a **deferred fast-follow**, not part of this phase, because deriving
  parameters at request time would move a historical-bar fetch onto the `202` path.
- **Q2 — partitions: 6.** Matches the observed scaling knees and leaves room for 6
  consumers.
- **Q3 — image: a separate worker image** (`Dockerfile.worker`). Rejected: second
  `@SpringBootApplication` in the same module — one classpath, one Fat jar, no
  independent scaling or versioning.
- **Q4 — autoscaling: CPU-based HPA now,** KEDA a later option. This is recorded with an
  explicit caution: write amplification measured at **≈ 22.75 MB WAL per simulation under
  load** (≈ 15x the stored payload) means CPU may saturate late or not at all
  (`docs/performance.md` §5). The HPA ships as recorded and **no scaling behaviour is
  claimed**.
- **Q6 — lease invariant:** `claim-lease = 12m` > `max.poll.interval.ms = 10m`
  > the slowest legitimate run, so a working consumer is never reclaimed mid-flight.
  Confirmed, not merely proposed.
- The API keeps the listener bean gated by
  `probity.simulation.worker.enabled` (default `true`), so the monolith can still run the
  consumer as one process if needed; deployments set it to `false`.
- Flyway has one owner: `probity-api` owns `db/migration`; the worker runs
  `spring.flyway.enabled=false` with `ddl-auto=validate`.
- The worker gets a dedicated DB role via `V18__worker_role_grants.sql`:
  `SELECT`/`UPDATE` on `simulations`, `SELECT` on the read tables and
  `flyway_schema_history`, no insert/delete and no `users` access. The role itself is
  created **out of band**; the migration is guarded and idempotent and skips grants when
  the role is absent.

Delivery semantics: `ack-mode: MANUAL_IMMEDIATE`, `max.poll.records: 1`, exponential
backoff retries, then `simulation-requested.DLT`; the recoverer marks a still-`PENDING`
row `FAILED`. `complete()` is fenced on the claim token, so a reclaimed row cannot be
double-written.

## Consequences

- API and worker scale, deploy and restart independently; the API keeps one Flyway chain.
- The worker is coupled to the monolith schema for its portfolio read. That is deliberate
  and reversible; Q1's fast-follow removes it.
- Two processes share one Postgres, so writes from one side are visible load on the other.
  Replica scaling is **sub-linear** (1 → 3 = 1.83x, 3 → 6 = 1.19x,
  `docs/performance.md` §4) and the binding resource was **not isolated**.
- A graceful `SIGTERM` finishes the in-flight record (measured 0.53 s); the lease and
  park/seek path remain the backstop for an ungraceful kill.

## Measured evidence

- Baseline vs split, 1 and 2 consumers: `docs/performance.md` §3.
- Replica scaling 1/3/6: `docs/performance.md` §4.
- WAL amplification and the HPA caution: `docs/performance.md` §5.
- Write cost vs response cost (P0/P1/P2): `docs/performance.md` §6.
- Rolling restart under load, DLT empty, lag 0: `docs/performance.md` §9.
