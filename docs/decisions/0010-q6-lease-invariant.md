# ADR 0010 — Q6: claim-lease and max.poll.interval invariant

**Status:** resolved — invariant confirmed (PR-D0).

## Context

The simulation worker uses a claim lease of 12 minutes (`claim-lease = 12m`) and Kafka's `max.poll.interval.ms = 10m`. The invariant ensures a legitimate slow run is never reclaimed while still working.

## Decision

`claim-lease (12m) > max.poll.interval.ms (10m) > the slowest legitimate run`. Confirmed not merely proposed: a working consumer with a slow Monte Carlo computation will never be reclaimed mid-flight because the lease exceeds the poll interval (PHASE_4_WORKER_EXTRACTION.md line 17, "Q6 - invariant: confirmed - lease 12m > max.poll.interval.ms 10m > slowest legitimate run").

## Consequences

- The worker can safely run simulations longer than 10 minutes without risk of automatic reclaimed.
- If a worker is killed ungracefully (no SIGTERM), the 12-minute lease is the backstop — after 12 minutes the row may be reclaimed, and the DLT path handles the resulting inconsistency.
- Graceful SIGTERM finishes the in-flight record before the consumer closes (measured: 0.53 s, `docs/performance.md` §9).

## Evidence

- PHASE_4_WORKER_EXTRACTION.md line 17: "Q6 - invariant: confirmed - lease 12m > max.poll.interval.ms 10m > slowest legitimate run."
- `application.yaml` `SIMULATION_CLAIM_LEASE:12m` and `max.poll.interval.ms=600000` (10 m).
- `docs/performance.md` §9: deterministic in-flight test — SIGTERM issued 0.1 s after `Received simulation request event`; the simulation completed 0.53 s later on the same replica, the offset was acked, then the consumer closed.

[Link: PHASE_4_WORKER_EXTRACTION.md line 17]