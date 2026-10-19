# Frontend Build Notes

Baseline repair: make `npm ci`, `npm run build` and `npm test` all succeed from a
clean clone, **without** `--legacy-peer-deps`, before this repo is imported into the
monorepo.

## Environment

| Item | Value |
| --- | --- |
| Node | `v20.20.1` (pinned in `.nvmrc`) |
| npm | `10.8.2` |
| git | `2.51.0.windows.2` |

`.nvmrc` pins the interpreter the checks were verified on; `package.json` declares
`engines.node: ">=20.19.0"`.

## Root cause 1 — `npm ci` failed with ERESOLVE

```
npm error code ERESOLVE
npm error While resolving: @vitejs/plugin-react@4.7.0
npm error Found: vite@8.0.5
npm error   dev vite@"8.0.5" from the root project
npm error   peer vite@"^5.2.0 || ^6 || ^7 || ^8" from @tailwindcss/vite@4.2.2
npm error Could not resolve dependency:
npm error peer vite@"^4.2.0 || ^5.0.0 || ^6.0.0 || ^7.0.0" from @vitejs/plugin-react@4.7.0
npm error Conflicting peer dependency: vite@7.3.7
```

`@vitejs/plugin-react@4.7.0` accepts `vite ^4.2.0 || ^5.0.0 || ^6.0.0 || ^7.0.0`, but the
root project requested `vite@8.0.5`. `@tailwindcss/vite` was not the problem — its peer
range does include `^8`.

**Regression source:** commit `48ccccd` ("update axios and uuid dependencies") bumped
`vite` from `6.3.5` → `8.0.5` and left `@vitejs/plugin-react` at `4.7.0`. The repo's own
`pnpm.overrides` still pinned `vite: 6.3.5`, i.e. `6.3.5` was the intended version.

**Fix:** `vite` back to `6.3.5` (one line). `@vitejs/plugin-react` stays at `4.7.0`.
Upgrading the plugin to `6.x` (which peers `vite ^8.0.0`) was rejected: it pulls in the
rolldown/oxc peer set and is a larger, riskier change than restoring the intended pin.

## Root cause 2 — `npm run build` failed under `--legacy-peer-deps`

```
[vite]: Rolldown failed to resolve import "react-dom/client" from ".../src/main.tsx".
```

`react` and `react-dom` were declared **only** as `peerDependencies` (marked optional via
`peerDependenciesMeta`). npm does not install a project's own optional peer dependencies,
so `node_modules/react-dom` was absent and `src/main.tsx`'s
`import { createRoot } from "react-dom/client"` could not resolve. They were present in
the lockfile only as transitive `"peer": true` entries (18.3.1), never as installed roots.

**Fix:** promoted `react` and `react-dom` to `dependencies` at `^18.3.1` — the version the
declared peer range and the lockfile already agreed on. For a private application the
`peerDependencies` + `peerDependenciesMeta` block was redundant, so it was removed.

## Versions changed

| Package | Before | After | Why |
| --- | --- | --- | --- |
| `vite` (dev) | `8.0.5` | `6.3.5` | Satisfies `@vitejs/plugin-react`; restores the intended pin |
| `react` | peer-only | `^18.3.1` (dep) | Required by `src/main.tsx`; was never installed |
| `react-dom` | peer-only | `^18.3.1` (dep) | Same |
| `vitest` (dev) | – | `^3.2.7` | Test runner; depends on `vite ^5 \|\| ^6 \|\| ^7` |
| `@testing-library/react` (dev) | – | `^16.3.3` | Component render/query helpers |
| `@testing-library/dom` (dev) | – | `^10.4.2` | Explicit peer of the above |
| `jsdom` (dev) | – | `^26.1.0` | DOM environment (v30 requires Node ≥22) |

No other dependency was upgraded, no application code was changed, and no refactoring was done.
`package-lock.json` was regenerated only as far as these changes require.

## Test setup

`vitest` + `jsdom`, wired to `npm test` → `vitest run`. `vite.config.ts` gained a `test`
block (`environment: 'jsdom'`) and imports `defineConfig` from `vitest/config` (which
re-exports Vite's, so the build is unaffected).

One smoke test, `src/smoke.test.tsx`, renders a small component and asserts the DOM. It
deliberately does not render the real `App`, whose providers (`AuthProvider`) issue network
calls on mount; the test's job is to prove the React toolchain is installed and working.

## Commands

```
npm ci        # exit 0, 389 packages
npm run build # exit 0
npm test      # exit 0
```

Build output (`vite v6.3.5`, 1773 modules, 2.05 s):

```
dist/index.html                 0.58 kB  | gzip:   0.34 kB
dist/assets/index-Ddexkjqg.css  151.65 kB | gzip:  24.99 kB
dist/assets/index-B5VOi8_V.js   413.79 kB | gzip: 122.65 kB
```

Test output:

```
✓ src/smoke.test.tsx (1 test) 43ms
Test Files  1 passed (1)
     Tests  1 passed (1)
```

CI (`.github/workflows/ci.yml`) runs `npm ci`, `npm run build` and `npm test` on pull
requests, using the Node version from `.nvmrc`.

## Follow-ups (not touched by this change)

Known API-drift and code-health items, listed only — none were modified:

- `check-email` route
- `run-async` flow
- CSRF on the assistant call
- simulations input range
- dead functions

Additional observations from this work:

- `react-dom` runs at 18.3.1 while `@types/react`/`@types/react-dom` are `^19`; the types
  and the runtime disagree. The build does not type-check, so this is latent. Decide the
  intended React major and align both.
- `npm audit` reports 16 vulnerabilities (1 low, 3 moderate, 10 high, 2 critical) in
  transitive dependencies. Pre-existing; not addressed here.
- `package.json` is still named `@figma/my-make-file`.
- `pnpm.overrides` is inert (no pnpm lockfile, npm is used). It now agrees with the pinned
  `vite`, so it was left as-is.
