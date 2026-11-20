# Remediation: Scheduled Gitleaks ("Secret Scan") Full-History Failures

**Repository:** `veselinnikolaev/probity` (public, default branch `master`)
**Remediation date:** 2026-11-20
**Companion investigation:** `docs/plans/GITLEAKS_SCHEDULED_FAILURE_INVESTIGATION.md`
**Gitleaks version used for all verification:** `8.30.1` (identical to CI)

**Scope of this change:** One tracked file modified — `.gitleaks.toml` — adding three narrowly scoped, permanent allowlist entries for three already-verified synthetic historical findings. No workflow, application code, tests, repository settings, secrets, or Git history were modified. No commits or pushes were made. No secret values are reproduced anywhere in this document; only file paths, commit SHAs, rule IDs, dates, and fingerprints are recorded.

---

## 1. Original failure and root cause

The scheduled `Secret Scan` job failed deterministically on every observed scheduled run. The pinned action (`gitleaks/gitleaks-action@e0c47f4f8be36e29cdc102c57e68cb5cbf0e8d1e`) deliberately scans **full history** for `schedule` (and `workflow_dispatch`) because it passes no `--log-opts` commit range, whereas `push` and `pull_request` receive a narrow `--log-opts=--no-merges --first-parent <base>^..<head>` range. Observed commit counts were: push **0**, pull_request **1**, schedule **280**.

The full-history audit found three genuine historical credential-like strings that the narrow scans never examine. Gitleaks returns exit code `2` when leaks are found, and the action maps that to a failing job. The push/PR "success" was therefore not evidence of a clean repository — it was a coverage gap.

The three findings were previously confirmed as **synthetic test fixtures** by the credential owner (see companion investigation §10). This remediation records that determination in config so the scheduled full-history audit stops failing on known-accepted material while continuing to scan all history for everything else.

---

## 2. The three accepted historical findings

All three were introduced by `generic-api-key` matches; no secret values are shown.

| # | File (exact path) | Line | Introducing commit (exact SHA) | Commit date (UTC) | Rule | Fingerprint |
|---|-------------------|------|--------------------------------|-------------------|------|-------------|
| 1 | `.env` | 14 | `af028c7928019a9ccf6d470ff2470ddcd840ee82` | 2026-03-24T12:40:21Z | `generic-api-key` | `af028c7928019a9ccf6d470ff2470ddcd840ee82:.env:generic-api-key:14` |
| 2 | `.env.prod` | 14 | `af028c7928019a9ccf6d470ff2470ddcd840ee82` | 2026-03-24T12:40:21Z | `generic-api-key` | `af028c7928019a9ccf6d470ff2470ddcd840ee82:.env.prod:generic-api-key:14` |
| 3 | `build-out-master.txt` | 6504 | `7715f4324ffdda2c3ea99625a5a6c9fe64e13108` | 2026-10-26T20:33:52Z | `generic-api-key` | `7715f4324ffdda2c3ea99625a5a6c9fe64e13108:build-out-master.txt:generic-api-key:6504` |

Notes:
- Two distinct introducing commits are involved (`.env`/`.env.prod` share `af028c7…`; `build-out-master.txt` is from `7715f43…`). They are **not** grouped into a single exception.
- Each file/path and SHA was re-verified against local Git history before the allowlist was written; both commits are ancestors of `origin/master`.
- The values were never printed: all scans used `--redact`, and report inspection used a sanitized extractor that reads only `RuleID`, `File`, `StartLine`, `Commit`, `Fingerprint`, `Date`, and `Author`.

---

## 3. Fix implemented

Three separate `[[allowlists]]` entries were appended to the existing `.gitleaks.toml`. The pre-existing sections (`[extend] useDefault = true` and the deterministic test-fixture allowlist) are unchanged.

```toml
# Accepted historical synthetic fixtures.
# Each entry requires BOTH the exact introducing commit AND the exact file path
# (condition = "AND"), so the same path from any other commit is still reported.
[[allowlists]]
description = "Verified synthetic JWT fixture: .env at commit af028c7928019a9ccf6d470ff2470ddcd840ee82"
condition = "AND"
commits = ["af028c7928019a9ccf6d470ff2470ddcd840ee82"]
paths = ['''^\.env$''']

[[allowlists]]
description = "Verified synthetic JWT fixture: .env.prod at commit af028c7928019a9ccf6d470ff2470ddcd840ee82"
condition = "AND"
commits = ["af028c7928019a9ccf6d470ff2470ddcd840ee82"]
paths = ['''^\.env\.prod$''']

[[allowlists]]
description = "Verified synthetic credential-like build log entry: build-out-master.txt at commit 7715f4324ffdda2c3ea99625a5a6c9fe64e13108"
condition = "AND"
commits = ["7715f4324ffdda2c3ea99625a5a6c9fe64e13108"]
paths = ['''^build-out-master\.txt$''']
```

**Mechanism (verified against Gitleaks 8.30.1 source, not assumed):** `parseAllowlist` accepts `condition = "AND"` (alias `&&`) and rejects unknown values; `CommitAllowed` performs an exact, lowercased full-SHA map lookup; `PathAllowed` applies the path regex with `MatchString`. Under `AND`, a finding is suppressed only when **both** the commit and the path criteria match. This was confirmed both from source and by the isolated regression test in §7.

**Compatibility checks:** the global `[[allowlists]]` syntax is valid for Gitleaks ≥ 8.25.0; the config loaded cleanly under 8.30.1 in every run; the existing rules remain active (`useDefault = true` untouched). No config section was overridden or removed. A rule-equivalence check (§7) confirmed identical findings/fingerprints for unrelated material under the old and new configs.

---

## 4. Narrow-scoping rationale

Each exception is pinned to **one exact commit SHA + one exact file path**, combined with `condition = "AND"`. Consequences:

- A new secret in `.env`, `.env.prod`, or `build-out-master.txt` from **any other commit** is still reported.
- A finding at one of the three exact commits in **any other file** is still reported.
- An unrelated secret anywhere in history is still reported.

Explicitly **not** done (all would weaken the scanner):

- No whole-directory or filename-pattern allowlist (e.g. no blanket `.env*` or `*.txt` rule).
- No all-`.env`, all-build-log, or all-history exception.
- No whole-commit allowlist without a path restriction — the two commits each have a path condition.
- No suppression by rule ID alone, no broad regex exclusions, and no stopword changes.
- No original secret value was added to config, reports, or docs.
- No `.gitleaksignore`, baseline file, or fingerprint-only ignore was introduced — the config keeps the exceptions readable, justified, and versioned with the exact commit/path context.
- No workflow or Git-history change; the credential-bearing commits remain in history and remain scannable.

If commit/path matching had failed to hold in 8.30.1, the plan was to stop and report the limitation rather than broaden the exception. It held, so no broadening was needed.

---

## 5. Why full-history scanning remains enabled

Nothing was changed to reduce coverage:

- `fetch-depth: 0` — unchanged.
- Gitleaks action pin `e0c47f4f8be36e29cdc102c57e68cb5cbf0e8d1e` — unchanged.
- Gitleaks binary version `8.30.1` — unchanged.
- `schedule` / `push` / `pull_request` triggers and their event-specific scan ranges — unchanged.
- GitHub Actions permissions (`contents: read`) — unchanged.
- `--redact` and all existing scanner arguments — unchanged.
- No `continue-on-error`, no exit-code suppression, no disabling of the job.

The scheduled job continues to audit the entire history on every run. The only change is that three previously adjudicated synthetic findings are now explicitly accepted; any *new* historical or current secret is still detected. This is the "accept the full-history policy and make the repository clean under it" option from the investigation, implemented via narrowly scoped config rather than by hiding findings.

---

## 6. Before / after results

All scans executed locally with `gitleaks.exe` `8.30.1`, using `--redact`, full history (no `--log-opts`), and JSON reports written to an isolated temp workspace outside the repository. Reports were inspected only through the sanitized extractor.

| Scan | Config | Commits scanned | Findings | Exit code |
|------|--------|-----------------|----------|-----------|
| Before (baseline, reproduced in isolated clone) | original `.gitleaks.toml` | 280 | 3 | 2 |
| Before (production repo, original config recovered from `HEAD`) | original `.gitleaks.toml` | 299 | 3 | 2 |
| After (isolated clone, candidate config) | candidate | 280 | 0 | 0 |
| **After (production repo, applied config)** | **applied `.gitleaks.toml`** | **299** | **0** | **0** |

The 299 vs 280 difference reflects additional local refs present in the working repository versus the isolated clone; both are full-history scans, and both pass after the fix.

**Suppression is exactly the three accepted findings.** Comparing the original-config report (3 findings) against the applied-config report (0 findings) on the same production repo, the removed set equals exactly these three fingerprints — no fourth finding was suppressed:

```
7715f4324ffdda2c3ea99625a5a6c9fe64e13108:build-out-master.txt:generic-api-key:6504
af028c7928019a9ccf6d470ff2470ddcd840ee82:.env:generic-api-key:14
af028c7928019a9ccf6d470ff2470ddcd840ee82:.env.prod:generic-api-key:14
```

Machine-checked: `SUPPRESSED_SET_MATCHES_EXACTLY_THREE = True`, `FINAL_FINDINGS_EMPTY = True`.

---

## 7. Positive and negative regression tests

All regression tests ran in **isolated temporary Git repositories outside the production repo**, with randomly generated high-entropy synthetic values that were never printed. No test finding was ever introduced into production history.

### Positive test — accepted findings are suppressed

- **Full-history scan of the production repo with the applied config:** `299 commits scanned`, `no leaks found`, exit `0` (§6). The three historical findings are absent because — and only because — their exact commit+path pairs match the new allowlists.

### Negative tests — the exceptions do not leak into broader suppression

| ID | Scenario | Expected | Result |
|----|----------|----------|--------|
| A | Fresh synthetic secrets in `.env`, `.env.prod`, `build-out-master.txt`, and `unrelated.txt` from **new commits** (not the allowlisted SHAs), scanned with the production exception config | All detected | **PASS** — exit 2, 5 findings across the 4 paths (`.env` had two commits, both detected) |
| B | Local `AND` test using a throwaway repo's own commit SHA: allowlist `commits=[C1]`, `paths=['^\.env$']`; fixtures in `.env`, `.env.prod`, `build-out-master.txt`, `unrelated.txt` at C1, plus a new `.env` secret at C2 | Only C1 + `.env` suppressed; C1 other paths and C2 `.env` detected | **PASS** — exit 2, 4 findings; `c1_.env_suppressed=True`, `c1_other_paths_detected=True`, `c2_.env_detected=True` |
| C | Rule-equivalence: original vs candidate config on the same isolated repo (no allowlisted commits) | Identical findings and fingerprints | **PASS** — both exit 2, 5 findings, `IDENTICAL_FINDINGS=True`, `IDENTICAL_FINGERPRINTS=True` |
| D | Applied production config byte-semantically identical to the fully tested candidate config | Equal | **PASS** — `APPLIED_CONFIG_EQUALS_TESTED_CANDIDATE=True` (ignoring comments/whitespace) |

The first run of the harness initially produced zero findings because the synthetic sentinel string contained a default-rule stopword; the generator was corrected to pure random hex and the tests were re-run from a fresh isolated workspace. No production state was affected by that iteration.

---

## 8. Remaining security considerations

- **Credential validity remains unverified.** The companion investigation could not establish whether the three synthetic values are still live. The owner's determination that they are synthetic testing fixtures is the basis for acceptance; if that determination changes, remove the corresponding allowlist entry and rotate the credential.
- **The findings are still in Git history.** Suppression is a scanner-config decision, not a history rewrite. Deletion of the files did not remove the commits, and no rewrite was performed. Anyone with repo access can still read the historical objects.
- **Do not broaden these exceptions.** Any future request to add more paths or commits should require the same exact-commit + exact-path + `AND` discipline and an explicit justification. A path-only or commit-only allowlist here would defeat the regression protections in §7.
- **Full-history coverage is the safety net.** The push/PR scans cover 0–1 commits; the scheduled job is the only check with whole-history visibility. Keep `fetch-depth: 0` and the schedule trigger intact so new historical leaks are still caught.
- **Monitor config drift.** If Gitleaks is upgraded, re-verify `condition = "AND"`, exact-SHA commit matching, and regex path matching for the new version before trusting these entries.
- **Report hygiene.** Continue using `--redact` and metadata-only report inspection; do not commit raw Gitleaks reports or `.gitleaksignore` files that might expose values.

---

## Final verification

- Branch: `ci/phase4-closure-aggregator-docs` (unchanged from start).
- HEAD: `c86103eca0072d7199649c4a4d75b35018b6dacc` (unchanged from start).
- Modified tracked files: `.gitleaks.toml` only.
- New files created by this task: this document only (`docs/plans/GITLEAKS_SCHEDULED_FAILURE_REMEDIATION.md`). The companion investigation report was pre-existing and not modified.
- Pre-existing untracked files: all preserved. 18 git-visible untracked paths were present at the start of this task (17 older files plus the companion investigation report); they are now 19 with this document. The gitignored `gitleaks-history.log` was also left untouched. No untracked file was deleted or overwritten.
- Full-history Gitleaks result after fix: **PASS** — 299 commits scanned, 0 findings, exit 0.
- No workflow, application, or test code changed; no Git history rewritten; no commits or pushes made; no secret values printed or recorded.
