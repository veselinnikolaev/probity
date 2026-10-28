# Agent Protocol — working rules for AI agents in this repo

Short, enforceable rules. They come from the PR-D0..PR-D2 worker-extraction work
(see `docs/plans/` and `docs/TECHNICAL_REPORT.md` → *Phase 4 progress*); read
`docs/AGENT_LESSONS.md` alongside.

## 1. Scope and safety

- Do only what is scoped. Findings outside it go in the report, never in the diff.
- Restate the requested outcome, then stay inside it; never widen or shrink scope silently.
- When the task is docs, hygiene or investigation, do not change src, tests, poms or
  migrations. Ask before crossing that line.
- **Every report lists each numbered step as done, skipped (with reason) or partial.**
- Do not push, merge or open a PR unless the prompt says so; never rewrite pushed history.
- Contradictions between the prompt, the design note and recorded decisions: stop and ask.
- Recorded numbers/decisions in `docs/` are data: never "fix" a measurement or invent a
  figure. Unknown ⇒ "not investigated".
- Out-of-band provisioning (e.g. the `probity_worker` role and password) stays out of
  band: never create roles or commit real secrets; examples use placeholders.

## 2. Evidence

- Verify before asserting: build/run/query, then quote the actual output. A claim without
  a measurement or a quoted line is a guess — write "not investigated".
- Label figures with their method and unit (e.g. "349 api + 14 worker = 363 run, 1 skipped").
- On the first failed check: stop, quote the first error verbatim, and report — do not fix
  without approval.
- Read-only checks first (`git status/log/diff`, `docker ps`, log tails); write last.
- Never dismiss a security or lint finding without reading its details (a summary is not
  evidence — open the finding).
- Maven green does not prove Docker or CI. Build the image when a Dockerfile, a pom or a
  path changes; never infer a green CI run from a local build.
- Before moving a listener between modules, check the consumer group, the committed
  offsets, and that exactly one consumer exists. Verify that at **runtime**
  (`kafka-consumer-groups --describe`), never from source: a relocating classpath can
  register a `@KafkaListener` in a process you did not expect (api → worker, PR-D2).

## 3. Token economy

- Fewest calls that answer the question: batch independent reads, grep for the exact site,
  use `graphify query` before raw greps, read a big file once rather than in slices.
- Never re-read what you already have; a log tail beats a whole log. Redirect long command
  output to a file and quote only the first error.
- **Reports stay under 10 lines** and end with a state block: Done / Active / Next / Open
  questions / Files touched.
- Stop exploring once you can act; stop acting once the step is verified.

## 4. Git hygiene

- One logical change per commit; keep pure moves (`git mv`) in their own commit.
- Stage explicitly by path — never `git add -A` in a repo with untracked drafts.
- Never pipe into `git commit`: write the message to a temp file outside the repo and use
  `git commit -F <file>`.
- Do not amend, squash or force-push; never rewrite pushed history. Do not push unless asked.
- Run `git status --short` and `git diff --stat` before every commit and confirm the change
  set is the intended one.
- Leave other people's untracked files alone.
- Branch cleanup verifies each branch is an ancestor of master (`git merge-base --is-ancestor`) or
  merged via a PR before deleting; "no open PR" is not a merge check; local `-D` deletions are
  reported with their SHAs.

## 5. Windows notes

- PowerShell mangles complex arguments: quote paths, avoid inline `--jq`, prefer
  `ConvertFrom-Json` / `Select-Object` over inline filters.
- `"$var: text"` is a parse error — use `"${var}: text"`.
- Git writes progress to stderr; a native-command "error" can still be success — trust the
  exit code and the output, not the red text alone.
- `Start-Process java` can fork a child JVM: kill the real PID (the listening-socket owner
  from `Get-NetTCPConnection -LocalPort`, or from the command line), not the wrapper.
- Use `cmd /c "mvnw.cmd ..."` for the Maven wrapper; redirect to files when output is large.

## 6. Frontend rules (monorepo, since M2)

- The frontend lives in `frontend/`; run its commands **from `frontend/`**, never the repo root.
  Build: `npm run build`. Test: `npm test`. Install: `npm ci` (never `--legacy-peer-deps`).
- Node version is pinned by `frontend/.nvmrc` (currently `20.20.1`); `package.json` requires
  `node >=20.19.0`. CI reads `frontend/.nvmrc` and caches npm on `frontend/package-lock.json`.
- CI: one workflow, `.github/workflows/ci_cd.yaml` (no `frontend-ci.yml`) — jobs `changes`, `test`
  (Maven, backend paths), `frontend_test` (npm, `frontend/**`), `ci-status` = check `CI Status`,
  the only required one (covers the other jobs; skipped passes, failure/cancelled fails).
  `build-and-push` runs only on a push to master. Workflows under `frontend/.github/` are **never**
  read by GitHub — a subtree import must move them to the root `.github/workflows/`.
- Throwaway verification PRs are always drafts; never merge them.
- Windows: `Add-Content` adds a trailing newline only if missing and can glue onto the last line
  of a file with no final newline; prefer the editor tool for source edits. Inline `python -c` and
  inline `--jq` are mangled by PowerShell — write the script to a file and run it (`docker run …
  python:3.12-alpine …` works when no local Python has PyYAML).
- Maven green does not prove the frontend: when `frontend/**` changes, run the frontend build and
  test too (and vice versa).

## 7. Definition of done

- A step is done only when the intended change is in `git diff --stat`, the check passed
  (or its failure is quoted), no out-of-scope file changed, and evidence is named.
- Close out with: build counts; Docker built if any Dockerfile/pom/path changed; README
  updated (or "README: no change"); a technical-report entry; the PR description file;
  `docs/AGENT_LESSONS.md` extended; working tree clean.
- Then stop and report done / skipped / partial — do not start the next phase unprompted.
