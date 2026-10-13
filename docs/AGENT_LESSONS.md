# Agent Lessons — mistakes and the rules they produced

Append-only. One entry = the failure, then the rule. Read together with
`docs/AGENT_PROTOCOL.md`. Do not rewrite old entries; add a new one.

## Seed list

1. **Skipped parts of a scoped task without saying so.**
   Rule: every report lists each numbered step as done, skipped (with reason) or
   partial.

2. **Docs kept claiming "354/354 passing tests" after PR-D1/PR-D2 moved and added
   tests.** Actual: 363 run, 1 skipped (349 api + 14 worker).
   Rule: when tests move or are added, update every test count in `README.md` and
   `docs/` in the same change.

3. **Post-split docs still pointed at the single-module jar
   `target/Probity-0.0.1-SNAPSHOT.jar`.**
   Rule: a module split must sweep jar paths in `README.md`, `loadtest/` and
   `docs/` — now `probity-api/target/probity-api-0.0.1-SNAPSHOT.jar` (API) and
   `probity-worker/target/probity-worker-0.0.1-SNAPSHOT-exec.jar` (runnable worker).

4. **Started the worker with the API's `DB_PASSWORD` and got
   `FATAL: password authentication failed for user "probity_worker"`.**
   Rule: the worker connects as the least-privilege `probity_worker` role with its
   out-of-band password (Q7) — never the API's `postgres` credential.

5. **Docs described the async consumer as in-process after it moved to
   `probity-worker`.** In the smoke test a simulation only completed because the
   worker was running.
   Rule: after extracting a process, state which process must run for the pipeline
   to finish.

6. **`Start-Process java` returned a wrapper PID; killing it left the real JVM
   (and its port) alive.**
   Rule: find the real JVM via the listening-socket owner
   (`Get-NetTCPConnection -LocalPort`) or the command line, and stop that PID.

7. **A worker health check alone is not evidence the pipeline works.**
   Rule: verify end to end — accept (202) → worker consumes → terminal status
   (`COMPLETED`) with consumer-group lag 0 — not just liveness.

8. **Temptation to change recorded measurement numbers while tidying docs.**
   Rule: annotate instead (e.g. the pre-extraction baseline caveat) — never edit a
   recorded number.

9. **A follow-up prompt referenced a previous prompt's steps, and the fresh
   context did not contain them.** The referenced spec (`AGENT_PROTOCOL.md` /
   `AGENT_LESSONS.md`) was absent from the prompt actually received.
   Rule: prompts are self-contained, and an agent that finds a referenced spec
   missing must stop and say so before reconstructing it.

10. **Regarded the listener as "moved out of the API" from a source-level grep,
    so the split baseline was first measured with the API still consuming.**
    The api → worker dependency puts `SimulationRequestedEventConsumer` on the api
    classpath and its component scan registers the `@KafkaListener` — invisible in
    the source layout, visible only at runtime: `kafka-consumer-groups --describe`
    showed **two members, 3 partitions each**.
    Rule: verify consumer topology at runtime (`kafka-consumer-groups`), never from
    source; a classpath move can register a listener in a process you did not expect.
