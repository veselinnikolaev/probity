# Probity Observability Audit

**Date:** 2026-10-31  
**Scope:** test-count reconciliation + local observability stack (metrics, logs, traces, alerts)  
**Out of scope:** new dashboards, Alertmanager, Kubernetes manifests, additional tracing infrastructure

This report separates three things that were previously mixed together:

1. **Verified** — observed by running a command against the running system.
2. **Configured but not verified** — code/config exists, runtime proof does not.
3. **Not implemented** — listed as future enhancements; not claimed as done.

> **Correction notice.** An earlier draft of this file claimed verified end-to-end trace
> propagation, a `probity_kafka_consumer_lag` metric, dead-lettered-record alerting, and
> schema-validated alert rules. Those claims were not supported by the code or the running
> system. Section 6 lists every correction.

---

## 1. Test Reconciliation

`.\mvnw.cmd verify -B` (full multi-module build):

| Metric | API | Worker | Combined |
|---|---|---|---|
| Discovered | 363 | 25 | 388 |
| Executed | 363 | 24 | 387 |
| Passed | 363 | 24 | 387 |
| Failed | 0 | 0 | 0 |
| Skipped | 0 | 1 | 1 |

Skipped test: `SimulationAsyncUnclaimableConcurrencyPlaceholderTest`, annotated
`@Disabled("PR-D1: reachable once listener concurrency > 1 is enabled (PR-D0 ships concurrency 1)")`.
It is an intentional placeholder, not a flaky or broken test.

Docs updated to this figure: `README.md`, `docs/operations.md`, `.github/agents/AGENT_LESSONS.md`,
`.github/agents/AGENT_PROTOCOL.md`.

---

## 2. Verified

Every row below was produced by a command against the running stack on 2026-10-31.

| Item | Evidence |
|---|---|
| Full build green | `mvn verify -B` → 388 discovered / 387 executed / 0 failed / 1 skipped |
| Compose file valid | `docker compose config --quiet` → exit 0 |
| Loki healthy after WAL fix | `GET :3100/ready` → 200; container `Up (healthy)` |
| Tempo healthy | `GET :3200/ready` → 200; container healthy |
| Tempo OTLP receiver accepts a payload | `POST :4318/v1/traces` → 200 |
| Promtail → Loki → query | Promtail ships Docker stdout to Loki. Loki queries for `{container="probity-backend-tempo-1"}`, `{container="probity-backend-prometheus-1"}` and `{container="probity-backend-simulation-worker-1"}` return real log lines; `container`/`service`/`project`/`stream` labels are populated from Docker metadata |
| Alert rules load and evaluate | Prometheus started with `--config.file` + mounted rules; `GET :9090/api/v1/rules` returns 3 groups / 5 rules, all `health=ok` |
| Prometheus API targets | 4 active: `probity` @ `host.docker.internal:8080` **up**, `probity` @ `app:8080` **up**, `probity-worker` @ `host.docker.internal:8081` **down**, `probity-worker` @ `simulation-worker:8081` **down** |
| Grafana provisioned datasources | Prometheus, Loki, Tempo declared with fixed UIDs in `monitoring/grafana/provisioning/datasources/datasources.yaml` |

Prometheus refuses to start when a rule file is invalid, so a successful start plus
`/api/v1/rules` is real parse-and-evaluate validation — stronger than reading the YAML.

`promtool` is not installed in this environment, so no offline `promtool check rules` result
is claimed.

---

## 3. Configured but Not Verified

| Item | What exists | Why it is not verified |
|---|---|---|
| **Application trace emission** | `management.tracing.*` / `management.otlp.tracing.*` in both `application.yaml` files | Neither module has `micrometer-tracing-bridge-otel` or an OTLP exporter on the classpath. `mvn dependency:tree` filtered for `tracing\|opentelemetry\|otlp` returns **no matches**. The config is inert: the apps emit no spans. |
| **API → Kafka → worker trace propagation** | W3C propagation settings; Tempo OTLP receiver | Depends on the row above, and the worker container is not running. Tempo accepting a synthetic POST proves the receiver, not propagation. |
| **Kafka consumer-lag metric** | Claimed in the previous draft | No `probity_kafka_consumer_lag` (or equivalent) exists anywhere in `probity-worker/src/main/java`. |
| **Dead-lettered-record metric** | `SimulationFailedRecordRecoverer` | It registers only `probity.simulation.dlt.publish.failures` — failures to publish *to* the DLT. There is no counter for records successfully dead-lettered. |
| **API log ingestion** | Same Promtail pipeline | The `app` container holds ~21 h of Docker history and its replay cursor is still near the start, so API log lines are not yet queryable. Historical entries outside Loki's ingestion window are dropped with `entry too far behind`. |
| **Grafana UI wiring** | Datasources + TraceID derived field | Grafana is reachable only inside the compose network in this environment; the derived field has not been exercised against a real span. |
| **Worker health/metrics scrape** | Targets configured | Worker container is in `Restarting (1)`; both worker targets are `down`. |

### 3.1 Alert-rule inventory after correction

| Group | Rule | Backing metric | State |
|---|---|---|---|
| probity-availability | `ProbityApiDown` | `up{job="probity"}` | verified |
| probity-availability | `ProbityWorkerDown` | `up{job="probity-worker"}` | verified |
| probity-simulations | `ProbityDltPublishFailure` | `probity_simulation_dlt_publish_failures_total` | verified to load; fires only when the worker runs |
| probity-http | `ProbityHttp5xxRateHigh` | `http_server_requests_seconds_count{application="probity"}` | verified to load |
| probity-http | `ProbityHttpLatencyP95High` | `http_server_requests_seconds_bucket{application="probity"}` | verified to load |

Rules that referenced metrics which do not exist (`ProbityKafkaConsumerLagHigh`,
`ProbitySimulationDeadLettered`) were **removed**. They would have loaded and stayed
permanently `inactive`, which is worse than no alert.

There is **no Alertmanager** in this stack: rules evaluate and display in the Prometheus UI
only. Nobody is notified.

---

## 4. Files Changed

| File | Change |
|---|---|
| `compose.yaml` | Declares `tempo`, `promtail`, mounts `prometheus-rules.yaml`, adds `tempo-data` / `promtail-positions` volumes, `grafana` depends on `tempo` |
| `monitoring/loki-config.yaml` | `ingester.wal.dir: /loki/wal` (fixes `mkdir wal: permission denied`); dev ingestion limits raised so the first Promtail replay drains instead of stalling |
| `monitoring/tempo.yaml` | New: single-binary Tempo, OTLP gRPC/HTTP, local storage |
| `monitoring/promtail-config.yaml` | New: Docker SD, JSON `level` label, no trace-id label |
| `monitoring/prometheus.yaml` | Loads `/etc/prometheus/rules.yaml`; targets for host-run and compose modes |
| `monitoring/prometheus-rules.yaml` | New: 5 rules over metrics that actually exist |
| `monitoring/grafana/provisioning/datasources/datasources.yaml` | Prometheus/Loki/Tempo with fixed UIDs, TraceID derived field, `tracesToLogsV2` |
| `probity-api/src/main/resources/application.yaml` | Restored Prometheus export flag; tracing config marked inert; removed invalid OTLP-metrics target |
| `probity-worker/src/main/resources/application.yaml` | Restored `management.endpoint.health.probes.enabled`; tracing config marked inert; removed invalid OTLP-metrics target |
| `README.md`, `docs/operations.md`, `docs/architecture.md`, `.github/agents/*` | Test-count reconciliation |

Removed scratch artifacts from the audit session: `monitoring/alloy-config.alloy` (a single
non-functional `url = ...` line), `verify_observability.py` (printed a fixed "PASS" narrative
regardless of results), `loki_push_test.txt`, `loki_series*.txt`, `test_loki.py`.

---

## 5. Reproduce

```powershell
# Stack
docker compose up -d kafka postgres redis loki tempo promtail prometheus grafana

# Readiness
Invoke-WebRequest http://localhost:3100/ready      # 200
Invoke-WebRequest http://localhost:3200/ready      # 200

# Alert rules actually loaded
(Invoke-RestMethod http://localhost:9090/api/v1/rules).data.groups |
  ForEach-Object { $_.name + ': ' + (($_.rules | ForEach-Object { $_.name + '[' + $_.health + ']' }) -join ', ') }

# Scrape health
(Invoke-RestMethod http://localhost:9090/api/v1/targets).data.activeTargets |
  ForEach-Object { "$($_.labels.job) $($_.scrapeUrl) -> $($_.health)" }

# Full suite
.\mvnw.cmd verify -B
```

---

## 6. Corrections Applied

| Previous claim | Reality |
|---|---|
| "End-to-end trace propagation validated via running stack with app+worker" | No tracing bridge/exporter dependency exists; no spans are produced. Not validated. |
| "`probity_kafka_consumer_lag` produced by AdminClient scheduled polling" | No such metric or polling code exists. Alert removed. |
| "Alert rules schema-validated against promtool 2.51.0" | `promtool` is not installed. What *is* verified: Prometheus loaded the rules and reports `health=ok`. |
| "9 alert groups" | 3 groups / 5 rules, each backed by a metric that exists. |
| "Promtail check on `json.level`" | The ECS JSON stage reads `log.level`. |
| "Worker started as compose service for verification" | Worker is in `Restarting (1)`; both worker targets are `down`. |
| "Loki query API operational" | Was asserted before any query ran. It is now verified for several containers, but not yet for the `app` container (backlog replay). |

---

## 7. Future Enhancements (Not Implemented)

None of the following is done. Do not treat any as delivered.

1. **Grafana Alloy migration.** Promtail remains the log shipper. Alloy was attempted and
   reverted because the config failed to parse in this environment; no working Alloy config is
   retained. Revisit with a pinned, tested Alloy version.
2. **Full API → Kafka → worker trace propagation.** Requires a tracing bridge + OTLP exporter
   in both modules and a running worker; then assert one trace contains the API span, the Kafka
   produce span, and the worker consume span.
3. **Runtime Kafka consumer-lag metric and alert.** Requires a real AdminClient/consumer-group
   lag gauge in the worker; only then can a lag alert be restored.
4. **Alertmanager integration.** No notification, silencing, or inhibition today.
5. **Individual Grafana dashboards.** No dashboard JSON provisioned; queries are manual.
6. **Kubernetes observability deployment.** The stack is Compose-only. K8s would need
   ServiceMonitor/PodMonitor, Kubernetes SD for logs, and ConfigMaps for Loki/Tempo/Prometheus.

---

**Bottom line:** the metrics path is real and verified for the API (Prometheus scrapes it, rules
load and evaluate, and every remaining alert is backed by a metric that exists). Log shipping is
verified end-to-end for several containers; the API container is still replaying its backlog.
The trace path is wired but produces no data, because neither application has a tracing bridge
or OTLP exporter on the classpath.
