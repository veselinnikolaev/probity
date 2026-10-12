# Agent Protocol — working rules for AI agents in this repo

Short, enforceable rules. They exist because of the PR-D0..PR-D2 worker-extraction
work (see `docs/plans/` and `docs/TECHNICAL_REPORT.md` → *Phase 4 progress*), and
are meant to be read before an agent touches this repository.

## 1. Scope and safety

- Restate the requested outcome, then stay inside it. If a step is out of reach,
  say so; never widen or shrink the scope silently.
- **Every report lists each numbered step as done, skipped (with reason) or
  partial.** Never imply completion you did not verify.
- When the task is documentation, hygiene or investigation, do not change
  production code, tests, poms or migrations. Ask before crossing that line.
- Treat numbers and recorded decisions in `docs/` as data: never "fix" a
  measurement, never invent a figure. Unknown ⇒ write "not investigated".
- Provisioning documented as out of band (e.g. the `probity_worker` DB role and
  its password) stays out of band. Never create roles or commit real secrets.
- Credentials in docs and examples are placeholders only.

## 2. Evidence

- Verify before asserting: build/run/query, then quote the actual output.
- Report what you ran, its exit status and the observed result — not what you
  expected. A claim without a command and its output is a guess.
- Read-only checks first (`git status/log/diff`, `docker ps`, log tails); write
  last. When something fails, capture the first error verbatim and stop.
- Prefer reproducible commands over prose.

## 3. Token economy

- Use the fewest calls that answer the question: batch independent reads, grep
  for the exact site instead of reading whole files, and use `graphify query`
  for codebase questions before raw greps.
- Do not re-read what you already have; a log tail beats a whole log.
- Read a large file once in a single call rather than in many small slices.
- Stop exploring once you can act; stop acting once the step is verified.

## 4. Git hygiene

- One logical change per commit; single-purpose, imperative subject line.
- Stage explicitly by path — never `git add -A` in a repo with untracked drafts.
- Never pipe another command into `git commit`: write the message to a temp file
  outside the repo and use `git commit -F <file>`.
- Do not amend, squash or force-push shared history. Do not push unless asked.
- Inspect `git status --short` and `git diff --stat` before every commit and
  confirm the change set is the intended one.
- Leave other people's untracked files alone.

## 5. Windows notes

- PowerShell mangles complex arguments: quote paths, avoid inline `--jq`
  strings, and prefer `ConvertFrom-Json` / `Select-Object` over inline filters.
- `"$var: text"` is a parse error — use `"${var}: text"`.
- Git writes progress to stderr; a native-command "error" can still be success —
  trust the exit code and the output, not the red text alone.
- `Start-Process java` can fork a child JVM: find the real PID from the listening
  socket (`Get-NetTCPConnection -LocalPort`) or the command line, not the wrapper.
- Use `cmd /c "mvnw.cmd ..."` for the Maven wrapper; redirect to files instead of
  piping when output is large.

## 6. Definition of done

A step is done only when: the intended change is present in `git diff --stat`,
the relevant check passed (or the failure is reported verbatim), no out-of-scope
file changed, and the report names the evidence. Then stop and report
done / skipped / partial — do not start the next phase unprompted.
