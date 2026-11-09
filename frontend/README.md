# Frontend README

This is the frontend for Probity, a portfolio risk platform.

## Environment

- **Node:** `v20.20.1` (pinned in `.nvmrc`)
- **npm:** `10.8.2`
- **Packages:** React and React DOM `^18.3.1`, matching React type packages, Vite `6.4.5`, TypeScript `^5.9.3`, Tailwind `4.1.12`, Vitest `^3.2.7`

## Build & Test (verified)

```bash
npm ci
npm run typecheck
npm run build
npm test
```

`npm run typecheck` performs a TypeScript check independently of the Vite production build. The lockfile is committed and should be refreshed with `npm install` whenever package versions change.
