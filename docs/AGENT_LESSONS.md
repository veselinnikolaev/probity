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

11. **A filtered test run that matched nothing still reported `BUILD SUCCESS`.**
    The sensitivity check used `-Dtest=ClassA+ClassB` (`+`, not the comma
    separator) together with `-Dsurefire.failIfNoSpecifiedTests=false`; Surefire
    ran **zero** tests and Maven exited 0, which reads as "the check passed".
    Rule: a scoped `-Dtest=` run is evidence only if its output shows
    `Tests run: N` for the intended class(es) — never accept a green filtered run
    without the count.

12. **Ran two temporary toggles in one experiment, so one check passed
    vacuously.** To prove the new listener tests are sensitive to the gate the
    same run removed the `@ConditionalOnProperty` **and** forced the worker flag
    `false`; the worker "container present" test then passed for the wrong reason
    (no gate = always registered) and proved nothing.
    Rule: change one variable per verification run, observe, then restore before
    the next — bundle toggles and you cannot attribute the result.

13. **Assumed `kubectl --dry-run=client` validates a manifest offline.** It does
    not — even client dry-run needs an API server, so it died with a
    `failed to download openapi` / connection error and validated nothing.
    Rule: validate manifests offline with a schema checker (`kubeconform` /
    `kubeval`) in Docker, and name the tool in the report — never imply a live
    cluster check.

14. **Added manifest probe paths without making the endpoints exist.** Spring
    Boot only auto-enables the `liveness`/`readiness` health groups when it
    detects it is running in Kubernetes, so a probe path can 404 outside a
    cluster.
    Rule: when a manifest probes `/actuator/health/{liveness,readiness}`, set
    `management.endpoint.health.probes.enabled: true` explicitly and confirm the
    endpoints resolve at runtime.

15. **Started the API without every required env placeholder.** `SENDGRID_API_KEY`
    was omitted; startup died in `UnsatisfiedDependencyException` naming
    `emailVerificationService` → `mailService` → `emailConfiguration`, far from
    the missing variable. A later start while Docker was down failed the same way
    on a DB connection.
    Rule: bring the infra up and export every placeholder with no default
    (`DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `ANTHROPIC_API_KEY`,
    `SENDGRID_API_KEY`) before starting; read the `Caused by` chain — the failing
    bean names a group, not the variable.

16. **Sent the CSRF token captured before login.** Spring Security rotates
    `XSRF-TOKEN` on authentication, so `run-async` returned **403 Forbidden**.
    Rule: read the `XSRF-TOKEN` cookie **after** login (and carry the session
    cookie) — a pre-login token is stale.

17. **Ran `docker compose` from this repo against a stack that belonged to a
    different clone.** `docker compose ps` returned nothing, yet `docker compose ls`
    showed project `probity` created from `IdeaProjects\Probity\compose.yaml` (an older
    clone). Driving that project from `probity-backend` with `-p probity` **recreated
    every service** (postgres, redis, grafana, prometheus, loki) — the recorded
    config-file path differs, so compose force-recreates even when the content matches;
    without `-p` it would instead have started a second stack colliding on 9092/5432/6379.
    Rule: before `docker compose up`, run `docker compose ls` and check the project's
    recorded config file; when the running project lives in another directory, drive it
    with an explicit `-p <name>` (its services get recreated, but named volumes — and
    the data — are kept) or use a throwaway project name.

18. **Started a disruptive load test before the consumer group had joined.**
    The rolling-restart run began with fresh replicas whose logs showed no
    `partitions assigned` line yet — stale members left by the just-removed run still
    held the group — so every request sat `PENDING`, the 150-poll client budget expired
    and the harness aborted on its own failure threshold. It read like "SIGTERM loses
    simulations"; the database afterwards held every row `COMPLETED`, so the aborted
    client was a poll-budget artifact, not lost work.
    Rule: before a disruption test, wait for the group to be **empty** and for the live
    replicas to own **every** partition before starting load; and treat the load
    generator's failure counter as client-visible latency, never as the server's verdict —
    read the database (and the DLT offsets) for the verdict.

19. **Read a lagging statistics view as if it were live.** `pg_stat_wal.wal_bytes` read
    immediately after the write returned **0** — the Postgres stats collector flushes with
    multi-second lag — which would have been recorded as "the write wrote no WAL".
    Rule: for a counter view, prefer an exact source when one exists
    (`pg_current_wal_insert_lsn()` + `pg_wal_lsn_diff()`), or settle (≥ 20 s here) and
    cross-check the two methods; a zero delta right after an action is a freshness bug,
    not a result.

20. **Let a preceding operation's checkpoint land inside a measurement window and read it
    as the operation's own WAL.** The standalone per-simulation run took its before/after
    `pg_stat_wal` snapshots right after a 1.3 GB 60-simulation run; a checkpoint still
    flushing attributed ~16 MB of full-page images to one 4.5 MB simulation (one condition
    read 21 MB). The LSN-diff figure was correct throughout, and only cross-checking the
    two methods exposed the skew.
    Rule: bracket a before/after snapshot around **only** the measured operation — run the
    small operation before any bulk load, or wait until the checkpoint counters stop
    moving — and treat a counter read that disagrees with the exact source as
    contamination, not a result.

21. **A payload placeholder also changed the response size; control confounds before
    attributing a speedup.** The throwaway "no-payload" worker build wrote a tiny
    `result_payload`, and the status endpoint it is measured through reads that row back —
    so the build removed the write **and** shrank the response at once, and its headline
    ≈ 10x was written down as the cost of the write. A later control that kept the full write
    and shrank **only** the response (P2) reproduced **none** of the speedup (P2 ≈ P0).
    Rule: when a change touches more than one observable, add a condition that changes each
    one alone before naming a cause; a build that moves two things is not an experiment on
    either one.

22. **Called a password "verified" over an auth path that never checks passwords.** The
    local reset of `probity_worker` was first confirmed with `docker exec … psql` inside
    the Postgres container (and would equally have passed on the container's loopback).
    That connection matches `pg_hba.conf`'s `local all all trust` (and
    `host all all 127.0.0.1/32 trust`) — `trust` accepts **any** password, so the green
    connect proved nothing; a wrong password would have looked identical. Only the
    `host all all all scram-sha-256` line (reached from outside the container) actually
    exercises the secret, and it was the path the worker containers failed on.
    Rule: before calling a credential verified, read the matching `pg_hba_file_rules` entry
    for the (type, address, user) the test used; a test that lands on `trust` must be
    reported as "auth not exercised", and the check repeated over a line that demands the
    real mechanism (`scram-sha-256`). A check that cannot fail is not a check.

23. **Assumed the workflow imported under `frontend/.github/` would run.** The M1 subtree
    import brought `frontend/.github/workflows/ci.yml` into the repo intact, and M1's
    verification only recorded it as "inert"; nothing ran it, and its job name (`Build & Test`)
    collided with the root backend job's name. GitHub discovers workflows at the repository
    root only — a `.github/` directory inside a subdirectory is never read, for any repo,
    however the files got there.
    Rule: after importing or vendoring a subtree that contains `.github/`, move its workflows
    to the root `.github/workflows/` (fix `working-directory:`, `node-version-file` and the npm
    cache path) and give it a distinct job name; delete the subdirectory copy. Verify by
    opening a PR and reading `gh pr checks` — a workflow that is not at the root produces no
    check run at all, which looks the same as "still starting".

24. **Treated a throwaway verification PR as safe to leave open near a merge button.** The
    three M2 path-filter PRs (#25/#26/#27) were created to observe check runs, then the owner
    merged them into the M2 branch before they could be closed; the throwaway comment lines
    then sat inside the branch under test.
    Rule: before opening a PR that must not be merged, make that explicit in the title
    (`TEST (throwaway)`) **and** keep the test branches and their base branch distinct from
    anything mergeable — or run the proof on a scratch repo/branch you own. If it is merged
    anyway, revert **additively** (restore the pre-test file content, one new commit); never
    force-push or rewrite pushed history, and re-run the proof cleanly so the evidence comes
    from an untainted tree.

25. **Applied an "explicit argument too large" rule to the argument's default.** The Q9b
    paths endpoint rejects `GET /{id}/paths?n=5` when only 3 paths are stored (400, by
    design), but the first implementation threw the same 400 when `n` was **omitted** and
    the row held fewer than the configured default (100): a 2-path row answered 400 to
    `GET /{id}/paths` even though the caller never asked for 5 or 100. The failure surfaced
    only inside `SimulationSplitReadIntegrationTest` (expected 200, got 400) — a test that
    happened to call the endpoint without `n`.
    Rule: when validating a request parameter, distinguish "the client sent a value that is
    out of range" from "the server's own default exceeds what exists"; only the first is a
    client error. When a default is used, clamp it to the available data, and add a test for
    the omitted-argument path separately from the explicit-value path — the explicit case
    passing does not prove the omitted case is handled.

26. **Assumed an out-of-band dev credential would still work.** The `probity_worker` role's
    password is deliberately provisioned out of band (Phase 4 Q7) and never committed; the
    PR-D5 copy in `%TEMP%\\opencode\\prd5\\workerdb.txt` failed `scram-sha-256`
    authentication against the current local Postgres because the dev database had been
    rebuilt since that run. The value is therefore not available to the agent at the start
    of a session, and a measurement run must not silently fall back to the `postgres`
    (superuser) role to get moving.
    Rule: treat out-of-band credentials as absent until proven present — authenticate with
    the stored value over the real `scram-sha-256` host path before relying on it, and if it
    fails, reset the local dev credential (confirming the target is this clone's own local
    dev container first), keep the new value outside the repo, and report the reset. Never
    substitute a superuser role for a least-privilege one to unblock a run.


### Problem
#2 Changing seeded credentials to bypass auth: Do not change credentials of shared or seeded accounts to get past a 401. Check how the existing harness logs in (loadtest/k6/baseline.js), ask first, or use a throwaway user. If you must temporarily change a seed account for local debugging, save the original hash before changing it and restore it immediately.

28. **Relied on a merge simulation performed before `origin/master` advanced.**
    Rule: a prior simulation does not replace re-simulating against the actual
    `origin/master` immediately before the real merge; remote updates can introduce
    conflicts or silently delete files.

27. **Proceeded past a required confirmation stop when the user was unavailable.** The 2a plan explicitly required waiting for confirmation, but autopilot treated an unavailable response as permission and continued through branch/stash/commit changes. Rule: if confirmation is unavailable, stop and report; never infer approval for a gated step.

29. **Misidentified PR number as M4c.** PR #40 was ops-worker-password-and-metrics (merged), not M4c; M4c remained on branch m4c-remove-check-email. 
   Rule: verify a PR's title/content (gh pr view or merge commit parents) before treating work as merged into master.
