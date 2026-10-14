# Probity simulation worker — Kubernetes manifests

Deployment, Service and HorizontalPodAutoscaler for the async simulation worker
(`probity-worker`), added by PR-D4 (Phase 4 §2.6). The worker has no business HTTP
API: it consumes the `simulation-requested` Kafka topic as a member of the group
`probity-simulation-worker`, and its only port is the actuator/metrics port
(`:8081`). Replicas are therefore the scaling unit — each pod owns a disjoint set
of the topic's **6** partitions.

These manifests were authored and validated but **not applied to a live cluster
from this repo** (there is no cluster in the build/test environment).

## Files

| File | Kind | Purpose |
| --- | --- | --- |
| `simulation-worker-deployment.yaml` | `Deployment` | 2 replicas, `RollingUpdate` (`maxUnavailable: 0`), `terminationGracePeriodSeconds: 660`, liveness/readiness probes, CPU/memory requests+limits. |
| `simulation-worker-service.yaml` | `Service` (ClusterIP) | Exposes only the actuator/metrics port (`:8081`) in-cluster for Prometheus scraping. No host port, no Ingress. |
| `simulation-worker-hpa.yaml` | `HorizontalPodAutoscaler` (`autoscaling/v2`) | CPU target 70 %, `minReplicas: 2`, `maxReplicas: 6` = partitions ÷ concurrency-per-pod. |

## Prerequisites

1. **Image.** Build and push the worker image (unchanged by PR-D4):

   ```sh
   docker build -f Dockerfile.worker -t <registry>/probity-worker:<tag> .
   docker push <registry>/probity-worker:<tag>
   ```

   Update `image:` in `simulation-worker-deployment.yaml` (or an overlay) to the
   pushed tag/digest. The committed value is `vesko49/probity-worker:latest`,
   matching `compose.yaml`.

2. **Kafka and Postgres** reachable at the hostnames in the Deployment's `env:`
   (`kafka:9092`, `postgres:5432`). Adjust for your cluster/namespace.

3. **`probity-worker-db` Secret.** The worker connects as the least-privilege role
   `probity_worker` (Phase 4 §2.2, **Q7**). The role **and its password are
   provisioned out of band** (ops/DBA) — never in a migration and never committed.
   Create the Secret separately, for example:

   ```sh
   kubectl create secret generic probity-worker-db \
     --from-literal=password='<probity_worker password>'
   ```

   The Deployment reads it via `secretKeyRef` (`key: password`). The role must
   exist before the api's grants migration (`V18__worker_role_grants.sql`) runs;
   that migration only grants privileges and skips the grants when the role is
   absent.

4. **metrics-server** installed, or the HPA cannot read CPU utilisation and stays
   at `minReplicas`.

## Apply

```sh
kubectl apply -f k8s/
```

(`README.md` is ignored by kubectl; only the `.yaml` files apply.)

## Verify

```sh
kubectl rollout status deployment/probity-simulation-worker
kubectl get deployment,service,hpa -l app.kubernetes.io/part-of=probity
```

Confirm the workers — and only the workers — own the topic partitions (run the
Kafka CLI inside the cluster, or externally against the broker):

```sh
kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
  --describe --group probity-simulation-worker --members
```

Expect **at most 6 members**, each owning a disjoint set of the 6 partitions, with
lag draining to 0. Probes resolve at `/actuator/health/liveness` and
`/actuator/health/readiness` (the worker enables
`management.endpoint.health.probes.enabled`).

## Sizing and the delivery-timeout invariant

| Knob | Value | Source |
| --- | --- | --- |
| Topic partitions | 6 | PR-D0 |
| Listener concurrency per pod | 1 | PR-D0 (raise only with the §2.7 placeholder work) |
| Replicas (start) | 2 | §2.6 |
| HPA `maxReplicas` | 6 = partitions ÷ concurrency | §2.6 |
| `max.poll.interval.ms` | 600 s | PR-D0 |
| claim-lease | 12 m | PR-D0 |
| `terminationGracePeriodSeconds` | 660 s | §2.6 (600 s poll window + margin) |

**Invariant:** `lease (12 m) > max.poll.interval.ms (10 m) >= slowest legitimate
run`. The grace period is deliberately above the poll interval so a scale-down or
rollout lets an in-flight record finish instead of relying on the lease to recover
it.

## Measured caveat — the CPU signal (§2.8.1)

The API scaling signal is **CPU**, but the recorded measurements do **not** show CPU
as the bottleneck: on the 1-replica baseline and the PR-D2 split run the worker used
only **0.23–0.28 cores** (one or two consumers), and two consumers improved
throughput by only **≈1.4–1.5×**. The ceiling looks bound by the Postgres
**WAL/disk write of the completion payload** (≈1.47 MB stored as compressed jsonb,
≈7.89 MB per row with TOAST + indexes), not worker CPU.

§2.8.1 requires this to be re-checked — capture `pg_stat_wal` /
`pg_stat_checkpointer` counters around one simulation and compare WAL bytes to the
stored payload — **before relying on the CPU-based HPA (Q4)**. If the write is the
real constraint, CPU utilisation may saturate late or not at all and the signal
must change (KEDA/Kafka-lag, or reduce the write cost per Q9). The HPA ships as
recorded; this re-check is an open follow-up.

## Follow-ups (not in PR-D4)

- **Kafka healthcheck bug** (`compose.yaml`): the `kafka` healthcheck runs
  `kafka-broker-api-versions`, which is not on `PATH` in `apache/kafka:3.7.0`, so
  the broker reports `unhealthy` even when it is reachable. Any `depends_on: kafka:
  condition: service_healthy` (the `app` and `simulation-worker` services) waits on
  it. Out of scope here; fix in a follow-up.
- **`.dockerignore`**: the worker/api Docker build context still includes
  `target/` and `.git` (noted in PR-D1).
- **Graceful-shutdown gap**: `terminationGracePeriodSeconds: 660` only helps if the
  JVM actually waits that long; Spring Boot's graceful-shutdown phase timeout /
  the Kafka listener container `shutdownTimeout` are shorter (not investigated).
- **Loki** crash-loops in the local compose stack (unrelated to the worker).
- **k6 re-baseline** for the N-replica and rolling-restart numbers (PR-D5).
