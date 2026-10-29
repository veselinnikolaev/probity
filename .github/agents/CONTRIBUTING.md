# Contributing

This project follows the [Agent Protocol](.github/agents/AGENT_PROTOCOL.md).

## Docs

- Architecture overview: `docs/architecture.md`
- Measured results: `docs/performance.md`
- Design decisions: `docs/decisions/`
- How to run the build and tests: see `docs/operations.md`

## Agent docs

Agent-level guidance lives in `.github/agents/`:
- `AGENT_PROTOCOL.md` — working rules for AI agents in this repo
- `AGENT_LESSONS.md` — lessons from previous agent sessions

These were moved from `docs/` via `git mv` and linked here.

## Local development

- JDK 21, `mvnw.cmd`.
- Node from `frontend/.nvmrc` (20.20.1), `npm install` in `frontend/`.
- `docker compose up -d` for integration stacks.
- Run tests: `mvnw.cmd test` (offline: `mvnw.cmd -o test`).

## Pull requests

- One logical change per commit; commit messages via temp file with `git commit -F <msg>`.
- Never `git add -A`; each logical change gets its own commit.
- Run the link checker over all `*.md` files before opening a PR.