# Frontend README

This is the frontend for Probity, a portfolio risk platform.

## Environment

- **Node:** `v20.20.1` (pinned in `.nvmrc`)
- **npm:** `10.8.2`
- **Packages:** React `^18.3.1`, React DOM `^18.3.1`, Vite `6.3.5`, Tailwind `4.1.12`, Vitest `^3.2.7`

## Build & Test (verified)

```bash
npm ci        # exit 0, 389 packages
npm run build # exit 0
npm test      # exit 0
```

Build output (Vite v6.3.5, 1773 modules, 2.05 s):
- `dist/assets/index-*.css` — 151.65 kB (gzip 24.99 kB)
- `dist/assets/index-*.js` — 413.79 kB (gzip 122.65 kB)

Test output:
- 1 smoke test passed (43 ms)

## Known

- `react-dom` at 18.3.1 while `@types/react`/`@types/react-dom` are `^19`; types and runtime disagree; build does not type-check.
- `package.json` still named `@figma/my-make-file`.
- `npm audit` reports 16 transitive vulnerabilities (pre-existing, not addressed here).
- `pnpm.overrides` is inert (npm is used); agrees with the pinned vite.

## Follow-ups (not touched)

- API-drift and code-health items (check-email route, run-async flow, CSRF on assistant call, simulations input range, dead functions).