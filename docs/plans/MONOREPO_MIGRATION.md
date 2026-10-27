# Monorepo migration plan â€” `probity-backend` + `probity-frontend` â†’ `probity`

**Status:** M1 import **done** (PR #24); M2 CI path filters **done** (PR #31); M3 rename/archive
**done externally**; M4a async flow **done** (PR #38); M4b input range, assistant CSRF and API
base URL **done** (PR #39); M4c check-email removal **done** (PR #41); M4d dead-code removal
and React type alignment **done**. The repository rename/archive was completed outside this
branch's commit history; the implementation milestones are recorded against their merged PRs.

Scope of this document: (1) a read-only audit of the frontend repository, (2) the
frontendâ†”backend drift list, (3) the Q9 frontend check, (4) the target layout and import
method comparison, (5) the per-file change list, (6) risks/rollback/checklist and the staged
PR sequence. Audited frontend commit: **`48ccccdf250eec1029770341b86a2406d7cc5094`**
(`veselinnikolaev/probity-frontend`, `master`, last commit 2026-06-19).

> The Â§1/Â§3 audit facts predate frontend PR #16 (2026-10-19), which fixed the broken install
> (`vite` 8.0.5â†’6.3.5, `react`/`react-dom` promoted to real deps, vitest smoke test, `.nvmrc`
> 20.20.1, CI workflow). See `frontend/FRONTEND_BUILD_NOTES.md`.

---

## 1. Frontend audit (read-only clone, facts cited)

### 1.1 Repository

| Fact | Value |
| --- | --- |
| Repo | `veselinnikolaev/probity-frontend`, default branch `master` + 16 remote `ai_main_*` branches, no tags |
| History | 81 commits, `git` pack 725 KiB, working tree â‰ˆ 0.94 MB â€” small enough to import whole history |
| Package name | `@figma/my-make-file` (`package.json:2`) â€” Figma Make artifact, never renamed |
| Committed noise | `.idea/` (JetBrains) committed; no `.gitignore` hygiene beyond the defaults |
| CI / deploy | **None** â€” no `.github/`, no Dockerfile, no Vercel/Netlify/Azure config in the repo |
| Lockfile | `package-lock.json` lockfileVersion 3 (`package-lock.json:4`); `pnpm.overrides` block exists (`package.json:90-94`) but npm ignores it |
| Node contract | no `engines`, no `.nvmrc`; README says Node 18+ (`README.md:93`) |

### 1.2 Stack

React 18 + TypeScript + Vite devDependency **8.0.5** (`package.json:76-77`), MUI 7.3.5,
Radix, Tailwind 4 (`@tailwindcss/vite`), axios (`package.json:12-45`).
`react`/`react-dom` are declared **only as optional peerDependencies** (`package.json:78-87`),
never as `dependencies` â€” a Figma Make export convention. Scripts: only `build` and `dev`
(`package.json:6-9`); **no test script, no test framework in devDependencies**.

Client configuration (`src/services/api.ts`): baseURL hardcoded `http://localhost:8080/api/v1`
(`:5`), `withCredentials: true` (`:6`), CSRF header `X-XSRF-TOKEN` read from the `XSRF-TOKEN`
cookie on mutations (`:15-17`), `Idempotency-Key` uuid per POST (`:23-24`).
`VITE_API_BASE_URL` is declared (`src/types/vite-env.d.ts:4`) but **never read anywhere**.
`vite.config.ts` has **no `server.proxy`** (`vite.config.ts:6-22`).

### 1.3 Quality snapshot (temp clone, Node v20.20.1 / npm 10.8.2)

- `npm ci --no-audit --no-fund` â€” **FAILED**. First error: `ERESOLVE Conflicting peer
  dependency: vite@7.3.7` â€” `@vitejs/plugin-react@4.7.0` peers on
  `vite@"^4.2.0 || ^5.0.0 || ^6.0.0 || ^7.0.0"` while the root devDep declares vite 8.0.5
  (`package.json:76-77`); the `pnpm.overrides` pin (`package.json:90-94`) does not apply to npm.
- `npm install --legacy-peer-deps` succeeded (304 packages, 23 s) but `npm run build` â€”
  **FAILED**. First error: `[vite]: Rolldown failed to resolve import "react-dom/client"
  from "src/main.tsx"` â€” with legacy peer resolution npm never installs the optional peers
  `react`/`react-dom`, which are not in `dependencies` (`package.json:78-87`).
- Tests: **not run â€” none exist** (no `test` script, `package.json:6-9`).

Net: the frontend does not install or build from a clean checkout with the documented toolchain
(`npm install`, `README.md:100`). Fixing this is a frontend-repo task, out of scope here.

### 1.4 What the frontend actually calls (both sides cited)

Backend canonical routes: `common/util/ApiRoutes.java` (auth `:15-22`, portfolios `:39-49`,
simulations `:64-68`, assistant `:72-73`).

| Frontend call | Defined at | Backend route | Verdict |
| --- | --- | --- | --- |
| `POST /auth/login`, `/register`, `/verify`, `/resend-verification`, `/refresh`, `/logout`, `GET /auth/csrf` | `services/auth/index.ts:6-44` | `ApiRoutes.java:16-22`, `AuthController` | match (CSRF-exempt set login/register/verify/resend: `SecurityConfiguration.java:70-75`) |
| `GET /auth/check-email` | `services/auth/index.ts:22-25` | **owner decision: remove the frontend call to GET /auth/check-email; no backend route is added (an unauthenticated e-mail existence check enables account enumeration)** | **removed (M4c implemented)** |
| Portfolio list/CRUD + summary/positions/composition/volatility/alerts/risk-metrics/correlation/var | `services/portfolio/index.ts:12-62` | `PortfolioQueryController.java:58-318` | match (VaR `confidenceLevel` sent as fraction `0.95`: `VaRReportPage.tsx:30` vs `@RequestParam defaultValue "0.95"` `PortfolioQueryController.java:318`) |
| `POST /simulations/run` | `services/simulation/index.ts:16` (only import in `useSimulation.ts:2`) | `SimulationController.java:71` â†’ 201 (`:95`) | match |
| `GET /simulations/{id}`, `GET /simulations` | `services/simulation/index.ts:24, 38` | `SimulationController.java:200, 228` | defined both sides but **dead frontend code** â€” never imported; no history/list route exists (`src/pages`: auth, dashboard, monte-carlo, portfolio(s), risk-analysis, settings, var-report only) |
| `POST /assistant/chat` | raw `fetch('/api/v1/assistant/chat', â€¦)` `AiAssistant.tsx:69-74` | `ApiRoutes.java:73` | **drift Ã—2** â€” relative URL bypasses the axios baseURL (`api.ts:5`) with no `server.proxy` (`vite.config.ts:6-22`) â†’ hits the Vite origin in dev; and the raw fetch sends only `Content-Type` (`AiAssistant.tsx:71`), no `X-XSRF-TOKEN`, while CSRF is enforced on everything except the four auth routes (`SecurityConfiguration.java:67-76`). Backend exempts this path from *idempotency* only (`IdempotencyFilter.java:76`), not from CSRF |
| Settings/me endpoints (`GET/PATCH /users/me`, password, sessions, preferences) | `services/settings/index.ts` | `ApiRoutes.java:28-32`, `UserSettingsController` | match |

Auth model: cookie session only (access/refresh cookies via `withCredentials`, `api.ts:6`) +
XSRF cookieâ†’header; no Bearer tokens, no localStorage tokens anywhere in `src/`.

### 1.5 Backend endpoints the frontend does not use

- `POST /api/v1/simulations/run-async` â€” `SimulationController.java:119`, returns **202 +
  `Location`** (`:153-154`); frontend has zero references to `run-async` or `/status` in `src/`.
- `GET /api/v1/simulations/{id}/status` â€” `SimulationController.java:170` (ETag/304 at `:182`).
- `GET /api/v1/simulations/{id}` and `GET /api/v1/simulations` â€” routes exist
  (`SimulationController.java:200, 228`) and the frontend *service functions* exist but are
  never imported (dead code, Â§1.4); `SimulationController.listSimulations` returns the **full
  payload including `allPaths`** per row.
- `GET /api/v1/portfolios/{id}` â€” `PortfolioQueryController.java:80`; `portfolioService` has
  no get-by-id function; detail pages fetch sub-resources instead.
- OpenAPI: springdoc enabled at `/api-docs` (`application.yaml:166-168`), served at runtime;
  **no committed OpenAPI file** in the repo, and the frontend has no codegen against it.
  Live endpoint response bodies: not investigated.

### 1.6 Validation drift

`numberOfSims` input: frontend `min="1"` / `max="100000"` (`MonteCarlo.tsx:127-130`), default
`DEFAULT_SIMULATION_COUNT = 10000` (`constants/simulation.ts:10`); backend
`@Min(100)` / `@Max(10_000)` (`RunSimulationRequest.java:21-22`). Values 1â€“99 and
10 001â€“100 000 are accepted by the UI and rejected with a 400. Response shape otherwise
matches: frontend `SimulationResult` (`simulation.types.ts:37-63`) â†” backend `SimulationResponse`
record (`SimulationResponse.java:11-42`); the frontend's `{success, data, error}` envelope type
(`simulation.types.ts:66-73`) is dead code â€” the backend returns the bare record.

---

## 2. Drift list (top findings, both sides)

1. **Frontend called an endpoint that does not exist.** `GET /api/v1/auth/check-email`
   (`services/auth/index.ts:23`) vs. no `check-email` mapping anywhere in
   `probity-api` (`ApiRoutes.java:15-22` has no such constant; literal grep: 0 matches).
   Register-form email availability therefore 404s in every environment.
   **Resolved (M4c):** live check removed; duplicate e-mail now detected at submit via
   `POST /auth/register` 409 ("Email already registered" / "Username already taken").
2. **The async simulation flow is invisible to the frontend.** Backend: `POST
   /api/v1/simulations/run-async` â†’ 202 + `Location` (`SimulationController.java:119, 153-154`)
   and `GET /{id}/status` polling with ETag/304 (`SimulationController.java:170, 182`).
   Frontend: sync-only `POST /simulations/run` (`services/simulation/index.ts:16`), no polling
   code, no 202 handling anywhere in `src/`. One side has a full async protocol the other
   never learned about.
3. **Dead code on both sides of the simulation list.** Frontend `fetchSimulation`/`listSimulations`
   (`services/simulation/index.ts:24, 38`) are never imported (no history route), while backend
   `GET /simulations` (`SimulationController.java:228`) returns full payloads with `allPaths` per
   row â€” the most expensive unused endpoint, and directly relevant to Q9 (Â§3).
4. **Assistant chat bypasses the axios client** (relative URL, no proxy, no CSRF header â€”
   Â§1.4), so it fails in dev (Vite origin 404) and is rejected by CSRF in production
   (`SecurityConfiguration.java:67-76`).
5. **Simulation-count validation mismatch** (Â§1.6): UI 1â€“100 000 vs backend 100â€“10 000.

---

## 3. Q9 frontend check (audited frontend commit `48ccccdf250eec1029770341b86a2406d7cc5094`)

All `allPaths` consumers in the frontend:

- `MonteCarlo.tsx:220` passes `allPaths` to **`FanChart` only** (sole consumer in `src/`).
- `FanChart.tsx`: y-axis min/max scale computed from all paths (`:46-51`); decorative
  spaghetti â€” *every* path drawn faintly at alpha 0.08, **no slicing** (`:95-104`);
  emphasised percentile lines and bands derive from `percentileSeries` (`:110-160`, using
  `allPaths[0].values.length` only for path length); tooltip reads `percentileSeries` (`:228-236`).
- Histogram reads `distribution` (`MonteCarlo.tsx:268`); `PercentileTable` and stat cards
  read `percentileSeries`/`statistics` (`MonteCarlo.tsx:253-280`).
- No CSV/PDF/export code anywhere (no export libs in `package.json`; grep: no matches).
- No history/list page exists (Â§1.4), so nothing else ever sees `allPaths`.

**Conclusion (exactly one): (1) sample paths plus bands suffice** â€” bands, tooltip, histogram,
table and cards all come from `percentileSeries`/`distribution`/`statistics`; `allPaths` is used
only for faint decorative lines and an approximate y-axis min/max (derivable from
`percentileSeries`/`statistics`), plus one `values.length` read (derivable from `timeHorizonDays`).
**Max N: today the code draws every path it receives, unsliced â€” the page requests 10 000 by
default (`constants/simulation.ts:10`), the input allows up to 100 000 (`MonteCarlo.tsx:130`)
but the backend caps at 10 000 (`RunSimulationRequest.java:22`), so effective max N = 10 000.**

---

## 4. Target layout

```
probity/                      (GitHub: veselinnikolaev/probity, renamed from probity-backend)
â”œâ”€â”€ pom.xml                   (reactor unchanged: probity-shared, probity-api, probity-worker)
â”œâ”€â”€ probity-shared/           (unchanged)
â”œâ”€â”€ probity-api/              (unchanged)
â”œâ”€â”€ probity-worker/           (unchanged)
â”œâ”€â”€ frontend/                 (imported history of probity-frontend)
â”‚   â”œâ”€â”€ package.json
â”‚   â””â”€â”€ src/
â”œâ”€â”€ .github/workflows/        (path filters added â€” Â§6)
â”œâ”€â”€ docs/                     (unchanged)
â”œâ”€â”€ Dockerfile, Dockerfile.worker, docker-compose.yml, k8s/, loadtest/
â””â”€â”€ README.md, DESIGN.md      (quickstart gains a frontend/ section)
```

- Module directories and the Maven reactor stay byte-identical. The root `pom.xml` lists
  `<modules>` **explicitly** (`pom.xml:23-27`), so Maven will not pick up `frontend/`;
  only an explicit `<module>frontend</module>` would â€” never add one.
- Frontend package name stays `@figma/my-make-file` initially (rename is a separate,
  optional frontend chore â€” not part of M1â€“M4).

## 5. Import method: `git subtree add` vs `git filter-repo`

| | `git subtree add --prefix=frontend --squash <url> master` | `git filter-repo --to-subdirectory-filter frontend` |
| --- | --- | --- |
| History | **Squashed to one commit** (full history reachable only via `git subtree split`) | **All 81 commits preserved**, rewritten to carry the `frontend/` prefix |
| Rewrite | No rewrite of frontend commits (squash commit is new) | Rewrites every frontend commit hash (SHA changes) |
| Push impact | Single new commit on `probity` â€” no force-push | Needs a fresh clone / orphan branch and a normal push of the branch; the old frontend repo is untouched either way |
| Effort on Windows | One command; low risk; needs network fetch of the frontend repo | Needs a second local clone of the frontend repo (`git filter-repo` is a Python tool, not bundled with Git for Windows) |
| Repo-size effect | One extra merge/squash in history; negligible (frontend pack 725 KiB) | Identical end tree; history slightly larger in bytes, better for blame |
| Revertability | `git rm -r frontend` + one commit removes everything; the squash commit can be reverted cleanly | Same removal, but 81 commits to purge from clones that already fetched |

**Recommendation:** `subtree add --squash` for M2 â€” history volume is trivial, but a
squashed import keeps `git log`/revert simple and avoids shipping 81 rewritten SHAs in the
first migration PR. Record the original frontend URL in `frontend/README.md` so
`git subtree split` remains available if per-file history is ever wanted. `filter-repo` is the
alternative if preserving per-commit history is a hard requirement â€” decide before M2, not during.

## 6. Per-file change list (when M2/M3 execute â€” none of this is done)

| File | Change |
| --- | --- |
| `.github/workflows/ci_cd.yaml` | Triggers are currently unfiltered (`:3-7`, push `master, develop` / PR `master`) and `mvn verify -B` (`:30`) runs per push. Add `paths-ignore: ['frontend/**']` to the Maven jobs (or `paths` filters) so frontend-only PRs don't run `mvn verify`; optionally add a separate `frontend` job (`npm ci` in `frontend/`) â€” note `npm ci` is currently broken (Â§1.3), so start with a placeholder or fix first. Secrets unchanged: `JWT_SECRET` (`:32`), `DOCKER_USERNAME`/`DOCKER_TOKEN` (`:54-55`). |
| `Dockerfile`, `Dockerfile.worker` | **No context change** â€” both build from repo root and `COPY` only `pom.xml` + `probity-*` (`Dockerfile:4-12`). Adding a `frontend/` directory to the root context grows the context by ~1 MB (working tree 0.94 MB) until `.dockerignore` excludes it (next row). A frontend build stage is *out of scope* (frontend has no Dockerfile and no deploy target today). |
| `.dockerignore` | Add `frontend` (the file already ignores `node_modules` at `:16` and lists exactly what the Dockerfiles COPY, `:1-3`) so the image context stays Java-only. |
| `.gitguardian.yaml` | `secret.ignored_paths` is `**/src/test/**` (`:10-11`). If the frontend ever gains tests with fixtures, add `frontend/**` equivalents; **no change required today** â€” frontend sources must stay scanned. Watch for false positives on `package-lock.json` noise; not observed (not investigated â€” frontend was never scanned by GitGuardian from this repo). |
| `README.md` | `:7` "This is the backend half â€¦ lives at probity-frontend" â†’ monorepo statement; Getting Started (`:208-264`) gains `cd frontend && npm ci && npm run dev` next to `./mvnw -o clean package -DskipTests` (`:247`); note the frontend's current broken install (Â§1.3). |
| `docs/AGENT_PROTOCOL.md` | Add a short frontend rules subsection: work only inside `frontend/` for UI changes; npm (not pnpm â€” `package-lock.json` is canonical, `pnpm.overrides` at `package.json:90-94` is inert); never run `npm install` outside the temp-clone rule for audits; frontend changes ride the same docs/PR discipline. |
| Hosting root dir | Whatever serves the SPA gets root `frontend/` and builds to `frontend/dist` (`npm run build`, `README.md:111`). No hosting config exists in either repo today (`frontend/README.md`, Vercel/Netlify: none) â€” the deployment root is *not investigated* beyond that; set it when a deploy target is chosen. |
| Env vars to re-create | Frontend: `VITE_API_BASE_URL` (declared `vite-env.d.ts:4`, currently unread â€” wire it into `api.ts:5` or drop it). Backend names (values never printed): `PORT`, `COOKIE_SECURE`, `SAME_SITE`, `APP_FRONTEND_URL`, `JWT_SECRET`, `ASSISTANT_CHAT_MEMORY_MAX_MESSAGES`, `CORS_ALLOWED_ORIGINS`, `IDEMPOTENCY_TTL_HOURS`, `SIMULATION_CLAIM_LEASE`, `SIMULATION_UNCLAIMABLE_BACKOFF`, `SIMULATION_CONSUMER_GROUP`, `SIMULATION_TOPIC_PARTITIONS`, `SPRING_DOCKER_COMPOSE_ENABLED`, `DB_HOST/PORT/NAME/USERNAME/PASSWORD`, `REDIS_HOST/PORT/PASSWORD/DATABASE`, `KAFKA_BOOTSTRAP_SERVERS`, `ANTHROPIC_API_KEY`, `SENDGRID_API_KEY`, `FROM_EMAIL` (`application.yaml:2-193`); CI secrets `JWT_SECRET`, `DOCKER_USERNAME`, `DOCKER_TOKEN` (`ci_cd.yaml:32, 54-55`). Renaming the repo does not invalidate GitHub Actions secrets; `APP_FRONTEND_URL`/`CORS_ALLOWED_ORIGINS` must be re-pointed when the hosted frontend URL changes. |

## 7. GitHub rename, archive and origin URL

Order matters (the name `probity` must be free on the account):

1. **Archive the old frontend repo** (Settings â†’ Archive) â€” *archive, never delete*; it keeps
   issues, the `ai_main_*` branches and the pristine 81-commit history as a rollback source.
   Archiving makes it read-only; the clone URL keeps working.
2. **Rename `veselinnikolaev/probity-backend` â†’ `veselinnikolaev/probity`** (Settings â†’
   Rename). GitHub keeps a redirect from the old URL; `gh` and Actions follow it automatically.
3. **Local remotes:** `git remote set-url origin git@github.com:veselinnikolaev/probity.git`
   (or the https equivalent) in every clone/worktree; verify with `git remote -v`. CI secrets
   and branch protection need no change (they follow the rename, not the URL).
4. Optional later: add a README note at the archived `probity-frontend` pointing at `probity/frontend/`.

## 8. Risks and rollback

| Risk | Mitigation / rollback |
| --- | --- |
| Name collision â€” `probity` already taken on the account/org | Check before M3; abort M3 only (import is independent of the name). |
| Maven accidentally picks up `frontend/` | Reactor lists modules explicitly (`pom.xml:23-27`); add no module. Verification: `mvnw -o clean verify` green after import (M2 check). |
| CI runs `mvn verify` on frontend-only PRs (slow, confusing) | Path filters in M2 (Â§6); safe to land after the import itself. |
| Docker build context bloat / accidental COPY of frontend | `.dockerignore` gains `frontend` in the same M2 commit; verify image size â‰ˆ unchanged. |
| Squash import hides frontend history | Original repo archived intact; `git subtree split` retrievable; document the original URL in `frontend/README.md`. |
| Broken frontend build propagates into the new repo | Not a migration risk per se â€” recorded as a known-broken state (Â§1.3); no CI job gates it yet, so nothing new breaks. |
| Force-push / history rewrite mistakes | M2 adds history, never rewrites it; no `filter-repo` unless chosen, and then only on a fresh branch pushed once. |
| Old repo deleted by mistake | Prohibited: archive only (Â§7.1); archived repos cannot be deleted without an explicit unarchive. |
| Rollback of any stage | M1: revert the docs commit. M2: `git rm -r frontend` + revert import commit (old repo still archived & intact). M3: GitHub rename is reversible in Settings (old name frees up after rename back); remotes re-point with `set-url`. |

## 9. Pre-migration checklist (all must pass before M2)

- [ ] Owner approves this document and picks the import method (Â§5).
- [ ] `git status` clean on `master`; `master` == `origin/master` (fetch first).
- [ ] Backend baseline recorded: `mvnw -o clean verify` BUILD SUCCESS (365 run, 1 skipped) at the M2 base commit.
- [ ] Frontend baseline recorded: HEAD `48ccccdf250eec1029770341b86a2406d7cc5094`, pack 725 KiB.
- [ ] `probity` name availability confirmed on the GitHub account.
- [ ] Frontend branches triaged: 16 `ai_main_*` remote branches â€” merged/stale confirmed or explicitly kept in the archive only.
- [ ] `.dockerignore`, CI path-filter snippets (Â§6) drafted in the M2 branch before push.
- [ ] Rollback owner named (who executes `git rm -r frontend` if M2 verification fails).
- [ ] Old frontend repo archiving scheduled (before M3, after M2 verification).

## 10a. M1 executed â€” import `frontend/` with full history (2026-10-19)

Owner redefined **M1 = the import** (this PR); Â§10's "M1 = docs / M2 = import" numbering is
superseded for the import step. Branch `m1-import-frontend`, base `master` @ `21be06b`
(PR #23, Q9a). **Not merged, no repo renamed or archived, M2â€“M4 not started.**

- **Command:** `git remote add frontend-origin https://github.com/veselinnikolaev/probity-frontend.git`
  â†’ `git fetch frontend-origin` â†’ `git subtree add --prefix=frontend frontend-origin master`
  (**no `--squash`**, history kept) â†’ `git remote remove frontend-origin`.
- **Imported commit:** frontend `master` = **`3b4506c123d6c9e63c205f74b0a46bea4526c1bf`**
  (PR #16 merge), tree `46dae9920ffb7b731ef127f901d599524914988f`, 86 commits.
- **Import commit:** `329f3a6` â€” a **merge** with parents `21be06b` (backend) and `3b4506c`
  (frontend), so all 86 frontend commits are reachable (`git rev-list --count 3b4506c` = 86).
- **Backup / rollback source:** bundle at
  `%TEMP%\opencode\m1-backup\probity-frontend.bundle` (743,923 bytes) built from a
  `git clone --mirror`; `git bundle verify` â†’ "The bundle records a complete history". This is
  a temp artifact local to the operator machine, not committed (see "surprises").
- **Only non-import change:** `.dockerignore` += `frontend/` (context hygiene; both images still
  build â€” see Â§10b).
- **Verification:** reactor unchanged â€” `mvnw.cmd -o clean verify -B` BUILD SUCCESS,
  **45 classes / 378 tests / 1 skipped** on *both* master and the import branch (delta = 0);
  `frontend/`: `npm ci`=0, `npm run build`=0 (`vite v6.3.5`, JS 413.79 kB / gzip 122.65 kB),
  `npm test`=0 (1 passed). Both Docker images built (exit 0).
- **Rollback:** branch unmerged â‡’ `git reset --hard 21be06b` (or delete the branch). If merged,
  `git revert -m 1 <merge-commit>` (a merge commit) â€” the frontend repo is untouched and still
  authoritative; the bundle re-imports at will.
- **Surprises:**
  1. `git log -- follow/-- <path>` on `frontend/**` shows only the merge until `-m` is added:
     subtree's merge records no rename, so `--follow` cannot cross it. History is intact â€”
     use `git log --oneline -m --follow -- frontend/<file>` or walk the second parent
     (`git log 3b4506c`). The plan's Â§10 "tree hash equals audited HEAD tree" check needs this
     caveat.
  2. The plan's baseline figure "365/378 run, 1 skipped" is stale after Q9a; measured baseline
     at the M1 base commit is **378 run, 1 skipped** (the per-module surefire `Results:` line
     reads 353 â€” that is the API module only; the repo-wide figure comes from per-class lines or
     the surefire XML).
  3. The frontend clone is *not* renamed/archived, so `frontend/` and the standalone repo
     coexist; a later M2/M3 must decide which is authoritative.
  4. `frontend/.github/workflows/ci.yml` is imported but **inert** (GitHub reads only the repo
     root `.github/workflows/`), so it neither runs nor needs deleting.

## 10b. M1 verification detail (facts, this branch)

| Check | Command | Result |
| --- | --- | --- |
| Reactor (import branch) | `mvnw.cmd -o clean verify -B` | BUILD SUCCESS, 45 classes / 378 run / 1 skipped; 0 frontend/npm/node_modules hits in output |
| Reactor (master `21be06b`) | same | BUILD SUCCESS, 45 classes / 378 run / 1 skipped |
| Frontend install | `frontend/`: `npm ci` | exit 0 (no `--legacy-peer-deps`) |
| Frontend build | `npm run build` | exit 0, JS 413.79 kB / gzip 122.65 kB |
| Frontend test | `npm test` | exit 0, 1 file / 1 test passed |
| API image | `docker build -f Dockerfile -t â€¦ .` | exit 0; `/app` = `app.jar` only (no frontend files) |
| Worker image | `docker build -f Dockerfile.worker -t â€¦ .` | exit 0 |

CI surfaces that now see `frontend/` (resolved in M2 â€” Â§10c):
- `.github/workflows/ci_cd.yaml` (root): no `paths`/`paths-ignore`, so a PR touching
  `frontend/` triggers `mvn verify` on the backend (harmless â€” the reactor ignores `frontend/`
  â€” but slow); and the `build-and-push` job runs only on push to `master`.
- `frontend/.github/workflows/ci.yml`: imported but inert (root-only discovery).
- `.gitguardian.yaml`: `ignored_paths: ["**/src/test/**"]` â€” **not path-scoped to the repo
  root**, so it also covers `frontend/src/test/**` if such paths ever appear; GitGuardian still
  scans all other `frontend/**` files (including `FRONTEND_BUILD_NOTES.md`).

## 10c. M2 executed â€” CI path filters for the combined repo (2026-10-20)

Branch `m2-ci-path-filters`, base `master` @ `38e6c6c` (the PR #24 merge). **Not merged; no repo
renamed or archived; M3/M4 not started.** Scope: workflows and docs only â€” no application code.

### What changed

| File | Change |
| --- | --- |
| `.github/workflows/frontend-ci.yml` | **new** â€” the frontend workflow, moved to the repo root (GitHub only reads `/.github/workflows/`) |
| `.github/workflows/ci_cd.yaml` | `changes` job (`dorny/paths-filter@v3`) + gated `test` job + new aggregated `ci-status` job; `pull_request` base-branch restriction dropped |
| `frontend/.github/workflows/ci.yml` | **deleted** â€” the inert imported copy (the whole `frontend/.github/` tree held nothing else) |

### Approach, and why

- **Workflow-level `paths:` for the frontend, step-level `paths-filter` for the backend.**
  A workflow that is filtered out at the workflow level posts **no check run at all**, which is
  cheap but invisible. So the frontend (a self-contained job) uses workflow-level `paths:`
  (`frontend/**`, the workflow file); the backend needs a *single stable, always-reported* check,
  so `ci_cd.yaml` keeps **no** top-level path filter and instead decides inside a `changes` job
  using `dorny/paths-filter@v3`.
- **Aggregated required check.** `CI Status` (`if: always()`, `needs: [changes, test]`) reports on
  every PR and passes when `test` was **skipped** (docs-only / frontend-only) or green, and fails
  only on `failure`/`cancelled`. This is the name branch protection should require â€” a
  path-filtered workflow that is skipped would otherwise leave a required check "expected" forever.
- **Existing job names kept.** `Build & Test` and `Build & Push Docker Image` are unchanged.
  `build-and-push` keeps its `push && refs/heads/master` guard â€” untouched.
- **`pull_request: branches: [ master ]` â†’ unrestricted.** Found while testing: a PR whose base is
  any other branch (a stacked PR) triggered **no** backend CI at all (only GitGuardian ran).
  `push` stays limited to `master`/`develop`.
- **Required status checks:** `gh api repos/â€¦/branches/master/protection` â†’ **404 "Branch not
  protected"**; `/rulesets` â†’ `[]`; `/rules/branches/master` â†’ `[]`. **No required check names
  exist today**, so nothing had to be preserved; `CI Status` is the name to configure if protection
  is added later.

### Verification â€” three throwaway PRs (all closed unmerged)

Each branch off `m2-ci-path-filters`, one comment line changed, checks read with `gh pr checks`:

| PR | Change | `Detect changes` | `Build & Test` | `Frontend Build & Test` | `CI Status` |
| --- | --- | --- | --- | --- | --- |
| #28 | docs only (`docs/plans/MONOREPO_MIGRATION.md`) | pass | skipping | â€” (not run) | **pass** |
| #29 | frontend only (`frontend/src/services/api.ts`) | pass | skipping | pass | **pass** |
| #30 | backend only (`probity-api/.../ProbityApplication.java`) | pass | pass (Maven) | â€” (not run) | **pass** |

The aggregated `CI Status` reported on all three; no check hung in "expected".

> A first round of throwaway PRs (#25/#26/#27) was created before the trigger fix and was merged
> into this branch instead of closed; the throwaway edits were reverted additively (commit
> `085133e`, no history rewrite â€” see `docs/AGENT_LESSONS.md`). Only their comment lines were
> removed; the CI evidence above is from the clean re-run.

### CI surfaces after M2

- A **docs-only** PR runs `Detect changes` + `CI Status` only (+ GitGuardian); Maven and Node skip.
- A **frontend-only** PR runs `Frontend CI`; the Maven `test` job skips.
- A **backend-only** PR runs the Maven `test` job; `Frontend CI` does not trigger.
- `.gitguardian.yaml` is unchanged (M1 note still applies).

### Rollback

Revert the M2 commits on the branch (or `git revert` them after merge); the workflows return to
the single unfiltered `ci_cd.yaml`. Nothing outside `.github/workflows/` and `frontend/.github/`
is affected.

## 10. Staged PR sequence

> **M1 = docs only** was the original plan; the owner then ran **M1 = import** (Â§10a).
> M2â€“M4 are proposals â€” execute nothing until prompted.

### M1 â€” docs (this PR)
- **Changes:** `docs/plans/MONOREPO_MIGRATION.md` (new), `docs/TECHNICAL_REPORT.md` entry,
  one Q9 line in `docs/plans/PHASE_4_WORKER_EXTRACTION.md` (labelled with the frontend commit
  hash; no decision change). README: no change. `docs/AGENT_LESSONS.md`: no change (the
  frontend install failure is a finding, not an agent mistake).
- **Verify:** `git diff --stat` shows docs only; no `src`, pom, test, migration, compose or
  workflow file touched; `mvnw -o clean verify` untouched (docs-only, not re-run).

### M2 â€” import `frontend/`  (**note:** largely done in Â§10a, but **without** `--squash`; the CI parts were done as Â§10c)
- **Done in Â§10a (M1-as-import):** `git subtree add --prefix=frontend frontend-origin master`
  (**no `--squash`**; the plan recommended `--squash` â€” owner chose full history);
  `.dockerignore` += `frontend/`.
- **Done in Â§10c (M2):** CI path filters â€” root `frontend-ci.yml` (frontend-scoped triggers),
  `ci_cd.yaml` `changes`/`ci-status` jobs, inert `frontend/.github/` deleted, PR base-branch
  restriction dropped.
- **Still open (deferred to M3+):** `frontend/README.md` header note with the original repo URL;
  root README quickstart section (M3).
- **Verify (as run, Â§10a/Â§10b/Â§10c):** `git log --oneline -3` shows the import merge `329f3a6`;
  `frontend/package.json` present; `mvnw.cmd -o clean verify -B` exit 0 (reactor untouched,
  378/1); both `docker build`s succeed; frontend tree hash = `46dae992â€¦` (audited
  `3b4506c^{tree}`); CI green on the PR; three throwaway PRs confirm the path filters.

### M3 â€” rename + archive
- **Changes:** archive `probity-frontend` (GitHub UI, no local change); rename
  `probity-backend` â†’ `probity` (GitHub UI); update `origin` URL in local clones;
  README link fix (`README.md:7`).
- **Verify:** `git remote -v` shows the new URL; `gh repo view veselinnikolaev/probity` OK;
  old URL redirects; Actions run green on the renamed repo; archived repo shows "Archive"
  banner and is read-only.

### M4 â€” optional frontend hygiene (separate repo-facing PR, frontend only)
- **Changes (each its own commit/PR or one scoped PR):** fix `npm ci` ERESOLVE (align vite
  version with `@vitejs/plugin-react`, move `react`/`react-dom` into `dependencies`, drop or
  fix `pnpm.overrides`); remove or implement `GET /auth/check-email`; fix `AiAssistant` fetch
  (use the axios client or send `X-XSRF-TOKEN` + absolute base URL); add the simulation-count
  bounds 100â€“10 000; delete dead `fetchSimulation`/`listSimulations`/envelope type.
- **Verify:** `npm ci && npm run build` exit 0 in `frontend/`; manual smoke: register, run a
  simulation, assistant chat, against `localhost:8080`.

## 11. Recommendation

M1-M4d are complete: M1 import (PR #24), M2 CI path filters (PR #31), M3 rename/archive
externally, M4a async flow (PR #38), M4b input range/assistant CSRF/API base URL (PR #39),
and M4c check-email removal (PR #41). M4d removes the confirmed-unused simulation APIs and
aligns the React type packages. The frontend install baseline remains recorded as historical
context; the closed standalone frontend types PR is superseded by the monorepo change.

## M4 drift (re-verified, 2026-10-22 and 2026-10-23, post Q9a/Q9b)

Re-checked against the merged Q9b tree (master `2b33a25`); line numbers are current.
The 2026-10-23 pass independently re-ran every item on branch `m4a-async-flow` and
reproduced all of (a)â€“(f) exactly; no drift changed and none was added.

- (a) async flow â€” drift, M4a. Backend: `SimulationController.runAsync` @123-161 (202 accepted + `Location`) and `getStatus` @175-205. Frontend: `useSimulation.run` @15-26 calls `runSimulation` (sync `POST /simulations/run`) @14-25 of `services/simulation/index.ts`; `MonteCarlo.handleRunSimulation` @78-86. No polling exists today.
- (b) assistant CSRF â€” drift, M4b. Frontend: `AiAssistant.tsx` @69-74 uses raw `fetch` with only `Content-Type` and no `X-XSRF-TOKEN`. Backend: `AssistantController` POST `/assistant/chat` (line 49) is CSRF-protected by the global filter, so the call only works because of the cookie; the raw fetch does not rotate/reuse the token like the axios client.
- (c) simulation-count range â€” drift, M4b. Frontend: `MonteCarlo.tsx` @165-167 allows `1..100000`; backend `RunSimulationRequest` @21-22 validates `100..10000`.
- (d) check-email — drift, **implemented (M4c)**. Frontend: `auth/index.ts` @22-26 and `Register.tsx` @72-97 called `GET /auth/check-email`; backend `AuthController` has no such route (routes list @11-30 of `ApiRoutes.Auth`). **Owner decision: remove the frontend call to GET /auth/check-email; no backend route is added (an unauthenticated e-mail existence check enables account enumeration).** **Implemented:** `checkEmailAvailability` removed from `auth/index.ts`; live check `useEffect` and `emailChecking`/`emailAvailable` state removed from `Register.tsx`; 409 handling on submit shows "Email already registered" / "Username already taken"; tests added in `Register.test.tsx` asserting 409 messages and no `check-email` calls.
- (e) dead code — drift, M4d. Frontend: etchSimulation @27-36 and listSimulations @38-45 in services/simulation/index.ts are unused; SimulationResponse envelope @78-86 unused. Backend: GET /portfolios/{id} exists (PortfolioQueryController @80-84) but no frontend caller was found; keep unless an owner wants it removed.
- (f) Q9b new drift — none. MonteCarlo.tsx already calls etchSimulationPaths @54 after the result lands; result/status/list bodies are paths-less and the /paths?n=100 usage is wired. No further frontend change needed for Q9b.

Proposed PR split: M4a async flow; M4b input range + assistant CSRF; M4c check-email (owner
decision made: remove frontend call, implemented and merged as PR #41); M4d dead code +
@types/react alignment (implemented and merged as the current branch's changes).

### M4c plan — replace live e-mail check with sign-up response error
The live GET /auth/check-email call is removed from the register form. Instead, duplicate e-mail is detected at submit time via the existing POST /auth/register 409 response.

**Files and lines to change (plan only, not implemented here):**
1. rontend/src/services/auth/index.ts:22-26 — delete checkEmailAvailability function.
2. rontend/src/pages/auth/Register.tsx:72-97 — remove the useEffect that calls checkEmailAvailability on email blur/change; remove the emailAvailable / checkingEmail state; change the submit handler to rely on 
egister() promise rejection with 409 → set form error "This e-mail is already registered".
3. rontend/src/pages/auth/Register.tsx UI state: remove the "Checking availability..." inline message and the green/red check icon next to the e-mail field.
4. Tests: add/extend Register.test.tsx to assert that a 409 from 
egister() renders "This e-mail is already registered" (no live check fired). Remove any test that mocked checkEmailAvailability.

**Behaviour after M4c:** user fills register form, submits; if e-mail exists, backend returns 409 with error body; frontend shows the error inline on the e-mail field — no separate live check, no extra round-trip, no enumeration vector.

### M4b status (2026-10-24) — implemented
- (b) assistant CSRF — **done**. Raw `fetch` in `AiAssistant.tsx` replaced with the authenticated axios client (`services/api.ts`) via a new `useAiAssistant` hook (`hooks/useAiAssistant.ts`). The axios interceptor attaches `X-XSRF-TOKEN` and `Idempotency-Key` automatically. Test added: `AiAssistant.test.tsx` verifies the POST goes to `/assistant/chat` through the authenticated client.
- (c) simulation-count range — **done**. UI input in `MonteCarlo.tsx` now enforces `min=100`, `max=10000` (matching `RunSimulationRequest.java:21-22`), with a validation hint. Constants `SIMULATION_COUNT_MIN=100`, `SIMULATION_COUNT_MAX=10000` added to `constants/simulation.ts`. Default remains 10000 (within bounds).
- (new) API base URL — **done**. `services/api.ts` now reads `import.meta.env.VITE_API_BASE_URL` with default `http://localhost:8080/api/v1`. Added `frontend/.env.example` with the variable. Updated README to reflect that the variable is now read and used.
