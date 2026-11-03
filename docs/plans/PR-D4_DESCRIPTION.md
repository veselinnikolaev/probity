# PR-D4 — Kubernetes manifests for the simulation worker (Phase 4)

> Draft PR description. Branch: `pr-d4-k8s`. Untracked — not committed.

## Scope

PR-D4 makes the async simulation worker **deployable and probe-able on Kubernetes**,
per `docs/plans/PHASE_4_WORKER_EXTRACTION.md` §2.6 (the Kubernetes paragraph) and the
§2.9 PR-D4 file list. The worker exposes no business API: it consumes
`simulation-requested` as a member of the group `probity-simulation-worker` and its
only port is the actuator/metrics port (`:8081`). Replicas are therefore the scaling
unit — each pod owns a **disjoint** set of the topic's **6** partitions.

It is a **deployment-only** change: concurrency stays at **1**, every
partitions / lease / poll / park / seek value is untouched, the PR-D3 listener gate is
untouched, and **no test is added or modified** (manifests are declarative; the probes
are verified at runtime). The only non-manifest change is one worker config line that
*makes the probe endpoints exist*.

## Files (new)

| File | Kind | What it sets |
| --- | --- | --- |
| `k8s/simulation-worker-deployment.yaml` | `Deployment` | `replicas: 2`; `RollingUpdate` `maxSurge: 1` / `maxUnavailable: 0`; `terminationGracePeriodSeconds: 660`; liveness `/actuator/health/liveness` + readiness `/actuator/health/readiness` on port `management` (8081); requests `500m`/`1Gi`, limits `2`/`2Gi`; `DB_PASSWORD` via `secretKeyRef`. |
| `k8s/simulation-worker-service.yaml` | `Service` (ClusterIP) | Exposes **only** the actuator/metrics port (`:8081`). No host port, no Ingress, no LoadBalancer. |
| `k8s/simulation-worker-hpa.yaml` | `HorizontalPodAutoscaler` (`autoscaling/v2`) | CPU target 70 %, `minReplicas: 2`, `maxReplicas: 6` = partitions ÷ concurrency-per-pod. |
| `k8s/README.md` | docs | Prerequisites, apply/verify, the delivery-timeout invariant, the §2.8.1 caveat, follow-ups. |

## The one runtime change

`probity-worker/src/main/resources/application.yaml`:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
```

**Why:** Spring Boot only auto-enables the `liveness`/`readiness` health groups when it
detects it is running **in** Kubernetes. The Deployment probes those exact paths, so the
flag is set **explicitly** — the endpoints then exist however the worker is deployed, and
they can be verified locally (which is how this PR verified them). No other line changes;
the file also gains a trailing newline.

## Secrets (Q7)

`DB_PASSWORD` is a `secretKeyRef` (`probity-worker-db`, key `password`). The
least-privilege `probity_worker` role **and its password are provisioned out of band** by
ops/DBA — **never** in a migration and **never** in a manifest. The api-owned
`V18__worker_role_grants.sql` only grants privileges (SELECT+UPDATE on `simulations`,
SELECT on the read tables) and **skips** the grants when the role is absent.

## The delivery-timeout invariant

| Knob | Value | Source |
| --- | --- | --- |
| `max.poll.interval.ms` | 600 s | PR-D0 |
| claim-lease | 12 m | PR-D0 |
| `terminationGracePeriodSeconds` | **660 s** | §2.6 (600 s poll window + margin) |

`660 s` is deliberately **above** the poll interval: a scale-down or rolling update lets
an in-flight record finish rather than relying on the 12 m lease to recover it.
`maxUnavailable: 0` keeps every partition owned during a rollout.

## Measured caveat — the CPU signal (§2.8.1)

The recorded 1-replica baseline and the PR-D2 split run do **not** show CPU as the
bottleneck: the worker used only **0.23–0.28 cores**, and two consumers improved
throughput by only **≈1.4–1.5×**. The ceiling looks bound by the Postgres **WAL/disk
write of the completion payload** (≈1.47 MB stored / ≈7.89 MB per row with TOAST +
indexes), not worker CPU. §2.8.1 requires re-checking this (`pg_stat_wal` /
`pg_stat_checkpointer` around one run) **before relying on the CPU HPA (Q4)**.

Decision (with the reviewer): **ship the HPA as recorded**, document the caveat in
`k8s/README.md` and the HPA manifest, and claim **no** scaling behaviour. The re-check is
an open follow-up.

## Verification

### Manifests (static)

`kubeconform` in Docker → **Valid: 3, Invalid: 0, Errors: 0**.
(`kubectl --dry-run=client` is **not** offline — it needs an API server — so it could not
be used; this is recorded as lesson 13.)

### Build

`mvnw -o clean verify` → **exit 0, BUILD SUCCESS, 177 s** (`Total time: 02:57 min`).

| Module | Tests run | Skipped |
| --- | --- | --- |
| `probity-worker` | 15 | 1 (the `@Disabled` placeholder) |
| `probity-api` | 350 | 0 |

**365 run, 1 skipped — unchanged** (PR-D4 adds no tests).

### Runtime against the compose stack

Image `probity-worker:pr-d4` (442 MB) built from the unchanged `Dockerfile.worker` and
run with `--network host` (so the broker's `localhost:9092` advertisement resolves),
connected as `probity_worker`:

```
/actuator/health           → {"status":"UP","groups":["liveness","readiness"]}
/actuator/health/liveness  → {"status":"UP"}
/actuator/health/readiness → {"status":"UP"}
```

```
GROUP                     CONSUMER-ID                                       HOST        #PARTITIONS
probity-simulation-worker consumer-probity-simulation-worker-1-a649f576-... /172.19.0.1 6
```

One member, partitions 0–5, `LAG=0` each. The worker log shows the rebalance assigning
all six partitions. One `run-async` reached **`COMPLETED`** (cold first run:
`e2e=7849 ms`, `server=748 ms`, `accept=689 ms`, `status_polls=4`).

Both artifacts were then stopped by their **real** pid (the listening-socket owner) /
`docker stop`; afterwards no socket on 8080/8081, no `java` process, and neither jar is
held open.

## Docs

- `docs/TECHNICAL_REPORT.md` — new `### PR-D4` entry and the progress header `PR-D0..PR-D4`.
- `README.md` — **no change**: no test count, jar path or startup instruction changes, and
  the k8s usage lives in `k8s/README.md` (as the loadtest usage lives in `loadtest/README.md`).
- `docs/AGENT_LESSONS.md` — four entries (13–16) for real mistakes made here.

## Out of scope / follow-ups

- **Not applied to a live cluster** (none in this environment).
- §2.8.1 CPU-signal re-check before the HPA can be relied on.
- `compose.yaml` Kafka healthcheck bug: the command is not on `PATH` in `apache/kafka`, so
  the broker reports `unhealthy` even when reachable; `app` and `simulation-worker`
  `depends_on: kafka: condition: service_healthy` wait on it. Reported, not fixed (out of
  scope, and no PR-D4 file is touched).
- `.dockerignore` (build context still includes `target/` and `.git`).
- **Graceful-shutdown gap:** `terminationGracePeriodSeconds: 660` only helps if the JVM
  waits that long; the worker's Kafka listener `shutdownTimeout` / Boot graceful-shutdown
  phase timeout are shorter (**not investigated**).
- k6 re-baseline for the N-replica and rolling-restart numbers → **PR-D5**.
