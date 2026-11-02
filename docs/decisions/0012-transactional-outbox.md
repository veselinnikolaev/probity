# ADR-0012: Transactional Outbox Pattern for Simulation Events

## Status
Accepted

## Context
The API accepts async simulation runs via `POST /simulations/run-async`, which inserts a `PENDING` simulation row into PostgreSQL and returns `202 Accepted` immediately. The worker consumes simulation events from Kafka, claims rows via `claimForProcessing`, runs the simulation, and completes them.

**Problem:** Steps 1 (insert simulation row) and 3 (publish to Kafka) are not atomic. If the application crashes after the PostgreSQL commit but before the Kafka send completes, or if Kafka is temporarily unavailable, the `PENDING` row has no corresponding event in Kafka. The row stays `PENDING` forever — the user polls `/status` endlessly, and the DLT/FAILED machinery is never reached because the record never entered Kafka.

The reliability assessment ranks this as the highest-severity failure mode (F1, S1 × M = 8), and it is the root enabler of three of the top-four ranked failures (F1, F2, F6).

## Decision: Transactional Outbox Pattern
The outbox pattern inserts the event into a local database table (`simulation_outbox`) in the **same transaction** as the simulation row. A separate publisher process reads pending outbox records and publishes them to Kafka, marking each as published only after successful Kafka acknowledgement.

### Why not synchronous Kafka publication?
- Synchronous `kafkaTemplate.send()` with `.get()` would block the HTTP thread, increasing request latency and tying up the Spring thread pool.
- A broker blip would cause every simulation acceptance to fail or time out, degrading availability.
- The client receives a non-2xx even though the business intent (accept the simulation request) was valid.

### Why not at-least-once with client-side deduplication only?
- The current idempotency filter caches the 202 for 24h as COMPLETED, which prevents client retries from re-driving the work (F2).
- Without a durable hand-off, the client has no way to know the request was accepted but not yet delivered.

### Why not a two-phase commit / XA?
- PostgreSQL and Kafka do not support XA/TwoPhaseCommit natively.
- Spring JTA is heavyweight, requires an application server, and adds significant operational complexity.
- The outbox pattern achieves the same durability guarantee without XA.

### Why not a transaction message listener (Kafka transactions)?
- Kafka transactions require the producer and consumer to be in the same session, which conflicts with the worker's per-message `manual_immediate` ack mode.
- Starting a transaction for every message adds latency and can cause coordinator timeouts under load.
- The outbox pattern is simpler and more compatible with the existing consumer configuration.

## Description

### Outbox table schema
```sql
CREATE TABLE IF NOT EXISTS simulation_outbox (
    id UUID NOT NULL,
    simulation_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    error_count INTEGER NOT NULL DEFAULT 0,
    claim_token UUID,
    claimed_until TIMESTAMPTZ,

    CONSTRAINT pk_simulation_outbox PRIMARY KEY (id),
    CONSTRAINT uq_simulation_id UNIQUE (simulation_id)
);

CREATE INDEX IF NOT EXISTS idx_simulation_outbox_pending
    ON simulation_outbox (created_at)
    WHERE published_at IS NULL;
```

- `id` is the primary key; generated UUID (often same as simulation_id, but independent to keep the outbox id stable).
- `simulation_id` links to the simulations row. UNIQUE ensures one event per simulation.
- `payload` contains the full `SimulationRequestedEvent` serialized as JSONB.
- `published_at` is null until the publisher successfully acknowledges the Kafka send.
- `error_count` tracks consecutive publication failure count. Used for alerting and exponential backoff; a row is never dropped when it reaches a threshold.
- `claim_token` is the fencing token of the publisher that currently owns this row. Cleared on publish; a stale publisher whose lease lapsed cannot overwrite a newer claim.
- `claimed_until` is the lease expiry. The row is claimable again once this passes; on failure it is pushed forward as exponential backoff, so a failed row stays queued and is retried forever.

### API change (`runSimulationAsync`)
The `runSimulationAsync` method now:
1. Begins a Spring `@Transactional` transaction (propagation = REQUIRED).
2. Calls `simulationRepository.save(pendingSimulation)` which inserts the `PENDING` row.
3. Constructs a `SimulationRequestedEvent` with the market data snapshot.
4. Inserts the event into `simulation_outbox` with `published_at = null` and `claim_token = NULL`.
5. Commits the transaction (both the row and the outbox record are now durable).
6. Returns 202 Accepted to the client.
7. After the HTTP response is sent, the `OutboxPublisher` picks up pending outbox records and publishes them to Kafka.
8. On successful Kafka acknowledgement, the publisher marks `published_at = now()` and clears `claim_token`.
9. If the Kafka send fails, `error_count` is incremented and the record stays unpublished for retry on the next publisher cycle.

### Publisher implementation
`OutboxPublisher.publishPendingEvents()`:
1. SELECT id, simulation_id, payload FROM simulation_outbox WHERE published_at IS NULL ORDER BY created_at ASC FOR UPDATE SKIP LOCKED LIMIT :batchSize.
2. For each record:
   a. `kafkaTemplate.send(topic, key, event).get()` — blocks until Kafka acks (acks=all default).
   b. IF success:
      - `simulationRepository.markOutboxPublished(id)` — in short transaction (REQUIRES_NEW): UPDATE published_at = now(), error_count = 0.
      - commits published_at, error_count = 0 immediately.
   c. IF failure:
      - `simulationRepository.recordOutboxFailure(id, token, retryAt)` — leaves published_at NULL; record stays pending.
      - error_count is incremented; claimed_until is pushed forward as exponential backoff.
3. Error count bounded at MAX_ERROR_COUNT=5; WARN log at threshold.
4. Continue until batch exhausted or timeout.
5. Sleep POLL_INTERVAL_MS, repeat.
6. @PreDestroy: cancel scheduled task gracefully.

### Idempotency and duplicate safety
- The worker's `SimulationRequestedEventConsumer.consume` already acknowledges duplicates (COMPLETED/FAILED → ack).
- The outbox has UNIQUE (simulation_id), so the database prevents inserting two events for the same simulation.
- If the publisher retries a record after a transient failure, the worker will see the event again but acknowledge it as a duplicate — no double-execution.
- If the publisher crashes after Kafka ack but before updating published_at, the next publisher cycle will re-send the event, and the worker will again acknowledge it as a duplicate.

### Restart resilience
- On application restart, any outbox records with published_at IS NULL are picked up and retried.
- Records with published_at IS NOT NULL are known to have been delivered (or at least attempted).
- The simulation row's PENDING status ensures the work is never lost — even if the publisher never runs, a reconciler can scan for orphaned PENDING rows.

### Configuration
- `spring.kafka.producer.acks`: `all` (already the Kafka default, but explicit in outbox config).
- `spring.kafka.producer.retries`: a finite number (e.g., 3) with exponential backoff, to avoid infinite retries on permanent failures.
- `spring.kafka.producer.max.block.ms`: 60000 (default), to prevent the publisher thread from blocking indefinitely.
- Outbox batch size and polling frequency are configurable properties (`probity.outbox.publish-batch-size`, `probity.outbox.poll-interval-ms`).
- `probity.outbox.initial-backoff-ms`: base initial backoff in ms (default 5000 in production).
- `probity.outbox.max-backoff-ms`: maximum backoff cap in ms (default 300000 = 5m in production).

## Consequences

### Positive
- **No silently lost work:** The simulation row and the outbox event are committed in the same transaction. If the app crashes after the commit, both are durable.
- **Recoverable Kafka outages:** The publisher retries; the 202 has already been sent, so the client is not blocked. On restart, pending outbox records are redelivered.
- **Duplicate delivery is safe:** The worker already acknowledges duplicates. The outbox's UNIQUE constraint prevents double-insertion.
- **No XA / TwoPhaseCommit needed:** Durability is achieved through the database transaction alone.
- **Backward compatible:** The worker code does not need to change. The consumer continues to claim and process events as before.
- **Observability:** `published_at` and `error_count` provide metrics for "how many accepted simulations are awaiting delivery."

### Trade-offs
- **Additional database table:** One new table (`simulation_outbox`) — minimal overhead.
- **Publisher component:** A new scheduled/polling service is needed — ~80-100 lines of Spring service code.
- **Slightly wider write path:** The `runSimulationAsync` method now does two DB operations (row + outbox) in one transaction — negligible latency impact.
- **Eventual consistency:** The 202 is returned before the event is actually in Kafka, but the outbox guarantees it will arrive. This is the same trade-off as the current fire-and-forget, but with durability.

### Risks mitigated
- F1 (PENDING row committed, Kafka publish lost → row stranded forever) → eliminated by the atomic transaction.
- F2 (Idempotency caches the 202, retry can never re-drive) → mitigated: a stranded row can be re-published from the outbox.
- F5 (DLT publish fails after row marked FAILED) → ordering improved: the event is in the outbox before the row is marked FAILED.

## Alternatives considered and rejected

| Approach | Score | Reason for rejection |
|---|---|---|
| Synchronous Kafka send (`future.get()`) | S2 | Blocks HTTP thread; every broker blip causes 5xx; degrades availability. |
| Kafka transactions (producer txn + consumer txn) | S2 | Incompatible with `manual_immediate` ack mode; requires coordinator; operational overhead. |
| Client-side retry with new idempotency key only | S2 | Idempotency filter caches 202 as COMPLETED for 24h; client cannot recover. |
| Two separate transactions (row + event) with manual reconciliation | S3 | Races between commit order; requires separate reconciler; not truly atomic. |
| Outbox + separate DLT topic | S3 | Adds complexity without addressing the core seam issue; DLT is a secondary concern. |

## Implementation risk
- **Low:** The outbox table schema is simple; the publisher is a straightforward polling service.
- **Medium:** Ensuring the publisher correctly handles in-flight messages across application restarts requires careful testing.
- **Low:** The worker does not need to change — the existing `SimulationRequestedEventConsumer` handles duplicates correctly.

## References
- RELIABILITY_ASSESSMENT.md §5 M1 — "Make the accept half not lose work"
- SimulationApplicationService.java `runSimulationAsync` method
- SimulationEventPublisher.java — current fire-and-forget publisher
- SimulationJpaRepository.java — `claimForProcessing`, `completeIfClaimHeld`, `markFailedIfPending`
- KafkaConfig.java — producer/consumer configuration