# Rename checklist - probity-backend  probity (owner steps only)

Preconditions: PR #31 merged, CI green, open PRs handled.

Owner steps:
1. Rename repo in GitHub Settings > General (probity-backend  probity)
2. git remote set-url origin https://github.com/veselinnikolaev/probity.git
3. Verify redirect from old URL (limit: new repo with old name breaks redirect)
4. Check required status checks after rename (CI Status)
5. Verify integrations
6. Archive probity-frontend, add README pointer to probity/frontend/ (optional PR)
7. Verify open PRs

Notes: Maven/package names unchanged, historical refs kept.
