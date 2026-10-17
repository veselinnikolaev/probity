# Q9 — Result payload storage (decision note)

Status: **OPEN** (this note does not decide). Docs-only: no `src`, test, `pom.xml`,
migration, `compose.yaml` or HPA change. The decision stays with the design/product
owner (see *Open questions*). This note gathers the facts Q9 needs — who consumes the
payload, how big each field is, how the result is returned today — and lays out the
options with what each changes, what breaks, migration needs, a *hypothesis* for the
measured cost and how to verify it (the recorded P0/P1/P2 protocol).

Q9 as recorded in `docs/plans/PHASE_4_WORKER_EXTRACTION.md` (§2.10.9): the completion
write of the ≈ 1.47 MB stored / ≈ 7.89 MB bytes-per-row `result_payload` is the
single-replica constraint, not worker CPU (§2.8.1); decide whether to store a summary
and percentiles instead of all paths before finalising the HPA signal (Q4).

---

## 1. Measured constraints (from `docs/PHASE_4_MEASUREMENTS.md`)

All figures below are recorded measurements; they are quoted, not re-derived.

| what | measured | where |
|---|---|---|
| raw JSON text of one payload (`octet_length(result_payload::text)`) | **4,270,030 B ≈ 4.27 MB** | *Payload size (measured)* |
| stored in Postgres (`pg_column_size`, compressed/TOASTed jsonb) | **1,472,297 B ≈ 1.47 MB** | *Payload size (measured)* |
| per-row table share (TOAST + indexes + heap slack) | **7,893,811 B ≈ 7.89 MB** | *Payload size (measured)* |
| `GET .../status` COMPLETED response body (`curl -w %{size_download}`) | **3,902,532 B ≈ 3.90 MB** | *Payload size (measured)* |
| completion `UPDATE` execute time (dominant variable cost) | **0.13 s ↔ 3.2 s** (Monte Carlo itself ≈ 30–80 ms) | *Payload write cost (why the session used to degrade)* |
| WAL per simulation, one **standalone** completion (LSN-diff) | **4,506,280 B ≈ 4.5 MB** (WAL : stored ≈ **3.06x**; WAL : raw ≈ 1.06x) | *§2.8.1 re-check* |
| WAL per simulation, **Scenario A** (60 completions) | **≈ 22.75 MB/sim** (LSN), 22.38 (settled `pg_stat_wal`); **≈ 73 % full-page images**, +2 checkpoints | *§2.8.1 re-check* |
| statements that write `result_payload` | **exactly 1** — the `completeIfClaimHeld` completion `UPDATE` | *Write-path investigation (2026-10-15)* |
| WAL knobs (`max_wal_size`/`checkpoint_timeout`, `wal_compression`, both) | cut under-load WAL **14–34 %**, **no throughput gain** | *Write-path investigation (2026-10-15)* |
| remove the completion write (P1, throwaway) | **≈ 10x** throughput at 1 replica, flat 1-vs-3 | *Payload-write diagnostic (2026-10-15)* |
| keep full write, shrink **only** the response (P2, throwaway) | **P2 ≈ P0** (≈ +10 %/+13 %); the ≈10x is the **write**, not the response | *Write cost vs response cost (2026-10-16)* |

**Consequence for Q9.** The cost the baseline is bound by is the **completion write of
`result_payload`** (one statement; a full rewrite of the TOASTed compressed jsonb into a
new row version — *§2.8.1*). The response size is a second, order-of-magnitude smaller
effect (≈10 %, *Write cost vs response cost*). Any storage option is judged on whether it
shrinks **what is written into `result_payload`**, not on what the client downloads.

---

## 2. Consumers of the result payload (read-only survey)

Frontend note: **there is no frontend source in this repository** (no `package.json`;
nothing under `probity-*`). `SimulationPayload`'s javadoc says it *"Mirrors the frontend
`SimulationResult` shape exactly"*, so a frontend exists as a **separate project not
present in this workspace** — its use of the fields is **not investigated here** (source
not in this repository).

Backend consumers, each with the fields it actually uses:

| # | consumer | where (file:line) | fields used |
|---|---|---|---|
| 1 | `POST /api/v1/simulations/run` (sync) | `probity-api/.../bff/controller/SimulationController.java` `mapToResponse` :297 (wired :70/:95) | **all three lists in full** — `allPaths` :308, `percentileSeries` :309, `distribution` :323 |
| 2 | `GET /api/v1/simulations/{id}` | same `mapToResponse` (:200/:216) | **all three lists in full** |
| 3 | `GET /api/v1/simulations` (list) | same `mapToResponse` (:228/:236) | **all three lists in full — for every row in the list** |
| 4 | `GET /api/v1/simulations/{id}/status` | worker `SimulationApplicationService.mapToResult` `probity-worker/.../service/SimulationApplicationService.java` :155–179 (`allPaths` :176, `percentileSeries` :177, `distribution` :178) | **all three lists in full** when `COMPLETED` |
| 5 | worker read mapper (DB row → DTO) | `probity-worker/.../simulation/mapper/SimulationMapper.java` :16 `toDto` | deserialises the **whole** `SimulationPayload` from the row for any read |
| 6 | DTOs that carry it | `SimulationResponse` (`probity-api/.../bff/dto/simulation/SimulationResponse.java` :17–21); `SimulationData` (`probity-worker/.../simulation/dto/SimulationData.java` :17–21); `SimulationStatusResponse.SimulationResponseResult` (:42–44) | the three lists plus statistics/outcomes/parameters |
| 7 | database column | `simulations.result_payload` jsonb (`SimulationJpaEntity`), written by `SimulationRepository.complete` (single `UPDATE`) | the whole payload |
| 8 | frontend | **not in this repository** (separate project) | **not investigated** |

- **Export / PDF / CSV / analytics:** none read the simulation payload. A grep for
  `pdf|export|csv|report|analytics` finds only the **VaR report**, which is computed from
  market data / returns, not from `result_payload`.
- **What every in-repo consumer uses:** they all pass **all three lists** straight
  through. No in-repo code reads *only* `percentileSeries` or *only* `distribution`; no
  in-repo code reads a *sample* of `allPaths`. The only in-repo consumer that would lose
  information if the lists shrank is the frontend (item 8, not investigated).
- **Contract detail that constrains any change (recorded):** both response mappers
  (items 1–4) dereference `payload.statistics()`/`payload.outcomes()`; an all-null
  `result_payload` **breaks the status contract**. Any summary-instead-of-paths change must
  keep a structurally valid payload or change those mappers
  (*Payload-write diagnostic* → key finding (c)).

---

## 3. Sizes by field (measured on one 1000-path × 90-day payload)

The database currently holds only P1 placeholder rows (300 rows, 424 B each — the last
diagnostic ran the P1 build), so a full-payload row could not be read back. The per-field
sizes below were measured by parsing **one real full payload captured in the Postgres
parameter log** from the write-path investigation (`prd6/logs/writes-check-raw.log`,
a 1000-path × 91-point run) and JSON-serialising each field compactly. Cross-check: the
whole-payload compact total is **3,899,671 B**, against the recorded status body
**3,902,532 B** (the ≈ +3 KB is the response envelope: `id`, `portfolioId`, `status`,
timestamps, `parameters`, `currentPortfolioValue`) — agreement within ≈ 0.07 %.

| field | JSON text bytes | share of payload |
|---|---:|---:|
| `allPaths` (1000 × 91 points) | **3,869,226** | **99.22 %** |
| `percentileSeries` (7 series × 91 points) | 27,360 | 0.70 % |
| `distribution` (30 buckets) | 2,678 | 0.07 % |
| `statistics` | 169 | 0.00 % |
| `outcomes` | 163 | 0.00 % |
| **whole payload (compact)** | **3,899,671** | 100 % |

Derived weights (this 1000 × 90 shape):

| variant | JSON text bytes | ≈ stored (jsonb) |
|---|---:|---:|
| summary only (`statistics` + `outcomes`) | **359** | tiny |
| summary + `percentileSeries` | **27,739** | ≈ tens of KB |
| summary + `percentileSeries` + `distribution` | **30,433** | ≈ tens of KB |
| full (all paths) | 3,899,671 | ≈ 1.47 MB (recorded) |

Per-path cost ≈ **3,869 B**; per-point cost ≈ **42.5 B** (`{"value":2147,"dayIndex":0}`).
`allPaths` is **99.22 %** of the JSON text, so *how many paths are kept* is the whole
question.

**Unit/label caveat (recorded).** The stored jsonb (**≈ 1.47 MB**) and the raw JSON text
(**≈ 4.27 MB** recorded / 3.90 MB here) are different numbers because jsonb stores object
keys once; the completion `UPDATE` writes the **stored** payload while the client
downloads the **response** body. Keep the label.

---

## 4. Current status / response flow and its size

- **Sync:** `POST /api/v1/simulations/run` computes the payload, saves a `COMPLETED` row
  (full jsonb) and returns the **full** `SimulationResponse` in the 201 body.
- **Async:** `POST /api/v1/simulations/run-async` → 202 `PENDING` (no result). The worker
  consumes the event, computes, and issues **one** completion `UPDATE` writing the full
  jsonb. `GET /api/v1/simulations/{id}/status` then reads the row and returns the **full**
  result (`mapToResult`), which is the body the load harness polls.
- **Measured size:** the `COMPLETED` status body is **3,901,505 B (1 replica) /
  3,901,916 B (3 replicas)** in the P0 block of *Write cost vs response cost* — i.e. the
  whole ≈ 3.90 MB result is returned on every poll, and the poll repeats until the client
  stops.

---

## 5. Options

Each option lists: **what changes**, **what breaks for consumers**, **migration for
existing rows**, **expected effect on the measured cost (hypothesis, not a result)**,
**risks**, and **how it would be verified** (the recorded P0/P1/P2 protocol:
`loadtest/run-baseline.ps1`, `TRUNCATE` before warm-up and each measured run,
observability stopped, one warm-up + 3 Scenario A (6 VUs × 60) runs per condition,
block-interleaved in one session, at 1 and 3 worker replicas; report throughput median
(min–max), e2e/accept p95/p99, `curl -w %{size_download}` status bytes, WAL per
simulation under 60-sim Scenario A, and api/worker CPU; a difference inside the run spread
is "not distinguishable").

### Option A — summary + percentiles only in `result_payload`; all paths offloaded (object store or separate table) written asynchronously

- **Changes:** `MonteCarloSimulationService` writes only summary + `percentileSeries`
  (+ maybe `distribution`) into `result_payload` (≈ 27.7–30.4 KB text, a valid payload that
  keeps `statistics`/`outcomes`); `allPaths` is written elsewhere (blob object storage, or
  a `simulation_paths` table) by a follow-up write the client does not block on.
- **Breaks / consumer impact:** `GET /{id}/status` and `GET /{id}` no longer carry
  `allPaths` inline; the list endpoint (`GET /simulations`) also loses them. Consumers that
  need all paths must call a **new endpoint** for the offloaded traces. The frontend is the
  unknown (item 8) — if its chart is driven by `allPaths`, this is a breaking API change
  unless a result endpoint is added in the same PR.
- **Migration:** none required to ship — old rows keep their full payload and still read
  back. (If the offload is a table, it needs a schema/migration for new rows.)
- **Expected effect (hypothesis):** removes 99.22 % of the payload, so the written column
  becomes tiny — the shape of the **P1** condition, which was **≈ 10x** at 1 replica. The
  offload write still happens, so the true gain depends on whether that write is cheaper
  than the current row rewrite; **this is the hypothesis to test, not a result.**
- **Risks:** a second write path to operate; offload may fail after the row is `COMPLETED`
  (needs retry/idempotency); an all-null placeholder **breaks the status contract**
  (recorded) — the summary must stay structurally valid.
- **Verify:** build a local-only summary variant and run the full P0/P1/P2 protocol against
  P0 (master); a P2-style control (keep the offload write, shrink only the response) is
  needed to separate write from response, exactly as *Write cost vs response cost* did.

### Option B — keep paths, but in a separate table (or separate column) rather than in `result_payload`

- **Changes:** `allPaths` moves out of the single jsonb column into a dedicated structure;
  the status/response mappers read summary from the row and paths from the new location.
- **Breaks / consumer impact:** same API surface questions as A; if the new table is read
  through the same endpoints, the contract can stay identical (paths reassembled), which is
  friendlier to the frontend than A.
- **Migration:** needs a new table/column plus a backfill for existing rows *only if*
  existing rows must also move; otherwise a forward-only change for new rows.
- **Expected effect (hypothesis):** **uncertain, and this is the key caveat.** The paths are
  still written; the gain exists only if writing them in the new shape avoids the
  full-row TOAST rewrite that *§2.8.1* identified. The write-path section found that
  cutting WAL bytes 14–34 % did **not** raise throughput, so a smaller write is **not**
  automatically a faster one. Treat "separate table is cheaper" as untested.
- **Risks:** two-place reads (join or two queries); transactional consistency between row
  and paths; the row-per-simulation read cost may move rather than fall.
- **Verify:** same protocol; compare against both P0 and P1 to see whether it lands closer
  to P0 (write shape unchanged) or P1 (write removed).

### Option C — paths compressed in a separate column (e.g. `bytea`, gzip) beside the summary

- **Changes:** `result_payload` keeps summary (+percentiles); a new `all_paths_compressed`
  column stores the paths gzip-compressed; mappers decompress on read.
- **Breaks / consumer impact:** if the API reassembles, the JSON contract is unchanged
  (frontend-safe); adds decompression CPU on every result read.
- **Migration:** new nullable column; existing rows keep `result_payload` full; backfill
  optional.
- **Expected effect (hypothesis):** lowers **stored bytes**, but *§2.8.1* already shows the
  completion write is a **TOASTed row rewrite whose cost is not WAL volume alone** (cutting
  WAL 14–34 % did not help). Compression changes size, not the rewrite pattern — so this
  option is **least likely to move throughput**; the honest expectation is "not
  distinguishable from P0" until measured.
- **Risks:** read-path CPU for decompression; a second representation of the same data;
  compression ratio depends on number formatting.
- **Verify:** same protocol; expect ≈ P0 (full write kept) unless the column write is
  somehow cheaper.

### Option D — status/summary stays as is; change only the API surface (see §6)

- **Changes:** storage untouched (full payload still written); the **status** response
  returns only the summary and a **new dedicated result endpoint** serves the full result
  on demand.
- **Breaks / consumer impact:** clients that read the result from `/status` must switch to
  the result endpoint; the DB write is unchanged, so existing rows need no migration.
- **Migration:** none.
- **Expected effect (hypothesis):** this is exactly the **P2** control, which reproduced
  **none** of the speedup (P2 ≈ P0, ≈ +10 %/+13 %). So it does **not** address the recorded
  write constraint — it is a response/downstream-cost change only.
- **Risks:** an extra round trip for clients that want the result; no effect on the write
  bottleneck.
- **Verify:** already run — *Write cost vs response cost* P2. Re-run only if the endpoint
  shape changes.

---

## 6. Separate small item — response-size reduction (summary status + dedicated result endpoint)

Independent of where paths are stored, the **response** can be split: return a summary in
`GET /{id}/status` and serve the full result from a dedicated endpoint. This was measured
as the **P2** condition: keeping the full write and shrinking only the status body moved
throughput by **≈ +10 % / +13 %** (P0 1.892 → 2.074 at 1 replica; 3.272 → 3.689 at 3), an
order of magnitude below the write effect. So it is a **real but small** win on the client
side, and it is **not** a substitute for changing what is written. Recording it here so it
is not confused with the storage decision.

---

## 7. Open questions (product owner only)

1. **Do users need all 1000 paths?** Or a sample (e.g. 100), the percentile band, or the
   distribution? This alone decides whether `allPaths` must be retrievable at all.
2. **What does the frontend actually render** — `allPaths`, `percentileSeries`, or
   `distribution`? (Source is not in this repository; **not investigated**.)
3. **How often is a stored result re-read after completion?** (Once, by the polling client?
   Re-opened later? Exported?) This decides whether an offload read latency is acceptable.
4. **Is a two-endpoint API acceptable** (summary status + dedicated result), or must
   `GET /{id}/status` remain a single call returning everything?
5. **Retention / loss tolerance for paths:** how long must all paths be kept, and is it
   acceptable to lose them (offload failure) after the summary is stored?
6. **Is the ≈ 3.90 MB status body a real client problem today** (mobile/network), or is the
   only real problem the server-side write?

---

## 8. Which option the measurements favour (naming only — no decision)

The measured constraint is the **write of `result_payload`**, and `allPaths` is
**99.22 %** of that payload. Therefore the measurements favour an option that **stops
storing all 1000 paths in `result_payload`** — **Option A** (summary + percentiles in the
row, paths offloaded, written asynchronously) — because it is the only family that removes
the dominant bytes from the one statement the baseline is bound by, and the closest
measured control (P1) shows removing that write is worth **≈ 10x**. Two honest caveats the
measurements also carry: (a) an offload is itself a write, so the real gain is a
**hypothesis** until the P0/P1/P2 protocol measures the variant; and (b) Options B and C
keep writing the paths and *§2.8.1*/**write-path** data (a row-rewrite cost that did not
move when WAL fell 14–34 %) suggest they may not move throughput — "smaller" is not
"faster" here. **No option is selected; the decision is the owner's.**

---

## 9. Provenance / method notes

- Consumer survey, field sizes, and the frontend absence were gathered read-only on
  `89a6474` (PR #19 merged). Field sizes came from a real payload captured in a Postgres
  parameter log (write-path investigation), cross-checked against the recorded 3.90 MB
  status body; they are not a fresh DB row (the current DB holds only P1 placeholders).
- No `src`, test, `pom.xml`, migration, `compose.yaml`, `README.md` or HPA change. No
  decision changed: Q9 stays open, Q4 (CPU-based HPA) is untouched.
