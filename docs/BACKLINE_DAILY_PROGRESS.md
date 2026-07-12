# Backline Daily Roadmap Progress

Last updated: 2026-07-12 (America/Chicago)

## Execution mode

One-shot implementation task: execute the remaining verified roadmap scope in one branch with small, independently verified commits. The operating contract is [BACKLINE_ONE_SHOT_IMPLEMENTATION.md](BACKLINE_ONE_SHOT_IMPLEMENTATION.md).

## Current task

Build and verify the isolated public landing site, reconcile the roadmap/progress records, and preserve the already-implemented backend quality work.

## One-shot commit checklist

- [x] Define one-shot scope, commit slices, acceptance criteria, and final report requirements.
- [x] Scaffold the independent `site/` project.
- [x] Implement the verified landing-page narrative and visual system.
- [x] Add content, browser, accessibility, and reduced-motion checks.
- [x] Add site-only CI and reconcile documentation.
- [ ] Push the final documentation/CI commit and merge PR #13 to `main`.

The historical baseline checklist below remains evidence of the completed inventory work; it is not a daily execution gate for this task.

## Ordered work-unit checklist

### Phase 1: Establish verified baselines

- [x] 1. Inventory repository modules, applications, libraries, workflows, migrations, Docker configuration, tests, performance tooling, and documentation.
- [x] 2. Run and record existing Gradle verification.
- [ ] 3. Run and record the full-stack E2E demonstration.
- [ ] 4. Run and record guardrail checks.
- [ ] 5. Run and record contract-drift checks.
- [ ] 6. Run a practical performance smoke test.
- [ ] 7. Confirm actual CLI commands and output formats.
- [ ] 8. Confirm the complete run lifecycle.
- [ ] 9. Inventory confirmed limitations.
- [ ] 10. Create the initial producer-to-consumer blast-radius map.

### Later phases

- [ ] Phase 2: Create the isolated website foundation (11 ordered work units).
- [ ] Phase 3: Establish the visual system (10 ordered work units).
- [ ] Phase 4: Build the landing-page narrative (9 ordered work units).
- [ ] Phase 5: Responsive, accessibility, and browser hardening (15 ordered work units).
- [ ] Phase 6: Site-only continuous integration (11 ordered work units).
- [ ] Phase 7: Static delivery preparation and deployment (13 ordered work units).
- [ ] Phase 8: Outbound-network policy (12 ordered work units).
- [ ] Phase 9: Secret and sensitive-data redaction (producer-to-consumer output-path work units).
- [ ] Phase 10: Risk-based coverage (7 ordered work units).
- [ ] Phase 11: Loopback-safe API defaults (audit, implementation, tests, and documentation work units).
- [ ] Phase 12: Operational metrics (audit plus bounded metric-family work units).
- [ ] Phase 13: Versioned CLI distribution (12 ordered work units).
- [ ] Phase 14: Documentation reconciliation (14 ordered work units and explicit deferred backlog).
- [ ] Final full-system reconciliation.

The automation prompt is the authoritative detailed ordering within later phases. Expand each phase here before its first work unit is selected.

## Completed work units

### 2026-07-11 — Phase 1.1 repository inventory

Verified the current repository topology without changing runtime behavior:

- Gradle applications: `apps/api`, `apps/cli`, `apps/worker`, and `apps/sample-api`.
- Gradle libraries: `libs/core`, `libs/config`, `libs/executor`, and `libs/reporting`.
- Root Gradle verification includes per-module tests, JaCoCo reports and thresholds, guardrails, and contract-drift checks.
- Workflow: `.github/workflows/backline-ci.yml`.
- Flyway migrations: `V1` through `V7` under `db/migration`.
- Runtime Docker configuration: root `docker-compose.yml` and Dockerfiles for API, worker, and sample API.
- Performance tooling: `perf/docker-compose.perf.yml`, PowerShell harness scripts, k6 scenarios, fixtures, and documented profiles.
- Repository verification scripts: audit-strength, guardrail, contract-drift, E2E demo, and performance smoke scripts.
- Product documentation: README plus API examples, audit playbook, CI integration, contracts, demo script, known limitations, and runbook.
- Test sources exist across all four applications and all four libraries: API 31, CLI 14, sample API 1, worker 6, config 4, core 3, executor 3, and reporting 2 (64 files total). Execution totals remain Phase 1.2.
- No `site/` directory exists yet, consistent with Phase 2 not having started.

### 2026-07-12 - Phase 1.2 existing Gradle verification

Ran the root Gradle verification path and recorded the current Java baseline:

- `.\gradlew.bat clean check` exits 0 after two Windows-portability fixes.
- Root `check` executes contract-drift checks, guardrail checks, all Java subproject tests, JaCoCo reports, and configured JaCoCo coverage verification.
- JUnit totals from generated XML: 221 tests, 0 failures, 0 errors, 56 skipped.
- Local skips are limited to Testcontainers-dependent API and worker tests when Docker is unavailable or not usable from this shell: API 40 skipped, worker 16 skipped. This remains consistent with documented local behavior; CI zero-skip enforcement is still a later roadmap item.
- Per-module test totals: API 99, CLI 36, sample API 6, worker 20, config 26, core 4, executor 26, reporting 4.
- Per-module line coverage from JaCoCo XML: API 63.2%, CLI 52.4%, sample API 87.5%, worker 42.6%, config 74.2%, core 29.0%, executor 76.9%, reporting 94.7%.
- Added `.gitattributes` to keep shell scripts checked out with LF endings, preserving Bash compatibility on Windows worktrees.
- Fixed `SampleServeCommandTest` to assert the platform-rendered jar path while preserving the command contract `java -jar <path>`.

## Current in-progress work unit

One-shot implementation task: final site-only CI verification, backend independence check, PR update, and merge to `main`. The historical Phase 1 checklist remains below as baseline evidence, not as a new daily stop condition.

## Deferred items

- All website, deployment, backend hardening, observability, packaging, and reconciliation work remains ordered behind the Phase 1 baseline.
- No expensive E2E or performance command was run during the inventory-only work unit.

## Blockers

None identified for Phase 1.2.

## Current blast-radius note

The one-shot site implementation verified the existing Gradle/site boundary and added no Java runtime coupling. Historical Phase 1.2 evidence remains recorded below.

## Commands executed

| Command | Exit code | Result |
| --- | ---: | --- |
| `git status --short` | 0 | Clean worktree; no output. |
| Repository document and topology inspection with PowerShell | 1 | Inventory output was produced, but the combined command ended on a missing `CODEX_HOME` environment variable; rerun used the known Codex home path. |
| Repository topology inspection with PowerShell | 1 | Useful inventory output was produced; a malformed PowerShell regex prevented test counting. No repository change occurred. |
| Corrected Docker, performance, docs, scripts, site, and memory inspection | 0 | Confirmed inventory; test-count subexpression emitted non-terminating regex errors, so no test totals are claimed. |
| `.\\gradlew.bat projects` plus read-only test/workflow/Docker inventory | 0 | Gradle topology command completed successfully. The combined shell capture returned no detailed stdout, so module claims rely on `settings.gradle` and module build files rather than inferred task output. |
| Corrected test-source count plus Git state and diff inspection | 124 | Counted 64 test source files, confirmed branch `main` and `origin`; the combined command timed out after `git diff --stat`, before status output. |
| Corrected per-module test-source count and progress-file review | 0 | Confirmed test sources in every declared subproject and reviewed the complete progress artifact. |
| `git switch -c codex/daily-roadmap-inventory`; stage; `git diff --cached --check`; commit | 0 | Created a scoped branch and commit; staged diff check passed. |
| Amend and `git push -u origin codex/daily-roadmap-inventory` | 0 | Pushed the verified documentation branch to `origin`. |
| `gh auth status`; `gh pr create ...` | 0 | GitHub authentication succeeded and pull request #13 was opened. |
| `.\gradlew.bat clean check` | 1 | Initial Phase 1.2 run failed before Java tests because `scripts/check-contract-drift.sh` had CRLF line endings in the Windows worktree and Bash rejected `set -euo pipefail`. |
| Shell-script line-ending inspection | 0 | Confirmed all five repository `.sh` scripts were checked out with CRLF despite LF in the index. |
| `bash scripts/check-contract-drift.sh`; `bash scripts/check-guardrails.sh` | 124 | Probe timed out at the wrapper level; contract-drift printed "Contract drift checks passed." before timeout. Root Gradle verification was treated as authoritative. |
| `.\gradlew.bat clean check` | 1 | After fixing shell line endings, Gradle reached Java tests and failed only `SampleServeCommandTest.buildCommandUsesJavaJarInvocation` due a Unix-specific path assertion on Windows. |
| `.\gradlew.bat :apps:cli:test --tests dev.backline.cli.commands.SampleServeCommandTest` | 0 | Focused CLI test passed after asserting `jar.toString()` instead of a hard-coded Unix path. |
| `.\gradlew.bat clean check` | 0 | Root verification passed: 61 actionable tasks, 60 executed, 1 up-to-date; 221 tests, 0 failures, 0 errors, 56 local Testcontainers skips; JaCoCo verification passed. |
| `git commit -m "test: record Gradle baseline verification"` | 0 | Created commit `952c8d3` with the Phase 1.2 verification record and portability fixes. |
| `git push` | 0 | Pushed `codex/daily-roadmap-inventory` from `61e1105` to `952c8d3`. |

## Verification results

- Targeted baseline: `.\\gradlew.bat projects` exited 0.
- Static cross-check: `settings.gradle` declares exactly eight Java subprojects, each with a `build.gradle` file.
- Inventory completeness was cross-checked against the root directory, workflow directory, migration directory, Dockerfiles, `perf/`, `scripts/`, and `docs/`.
- No tests were executed in this work unit; test execution and totals belong to Phase 1.2.
- Warnings: audit commands with scripting, capture, or timeout defects are described above. They did not mutate existing files, and affected claims were either rerun or explicitly not made.
- Phase 1.2 targeted baseline: `.\gradlew.bat clean check` exited 0 after correcting portability defects exposed by the baseline run.
- Test totals from XML: 221 tests, 0 failures, 0 errors, 56 skipped.
- Local skip distribution: API 40 skipped, worker 16 skipped. This is not marked as a blocker for Phase 1.2 because zero skipped tests in CI is already tracked as later roadmap work.
- Root verification included `checkContractDrift`, `checkGuardrails`, Java compilation, all subproject tests, JaCoCo reports, and coverage verification.
- Warning: Gradle reported deprecated features that will be incompatible with Gradle 9.0. This was not investigated in Phase 1.2 because the selected unit was baseline verification.

## One-shot implementation evidence

- `site/` is a standalone Vite + TypeScript static site with its own package manifest, lockfile, source, tests, build, browser checks, and README.
- The landing page uses CLI, API, worker, PostgreSQL, sample API, report, policy, status, exit-code, and limitation vocabulary verified in repository code and docs.
- Representative ledger rows and terminal output are labeled as representative; no hosted dashboard or monitoring claim was added.
- `.github/workflows/site-ci.yml` runs only for `site/**` changes and verifies the site without backend services.
- `README.md` links to the site documentation without duplicating the product README.

## Architectural decisions

- Treat the existing Java module graph as mature baseline state; do not redesign it without a confirmed defect.
- Keep the future public website outside `settings.gradle` and all Java module build files.
- Use one implementation task with small independently verified commits; the historical daily roadmap is retained only as baseline evidence.
- Keep Bash-based verification scripts pinned to LF endings through `.gitattributes`; otherwise Windows checkouts can break root Gradle verification before tests run.

## Website isolation evidence

- `site/` is outside `settings.gradle` and all Java module build files.
- `site/package.json` and `site/package-lock.json` define the only site dependency graph.
- Site build and browser tests run without PostgreSQL, Spring Boot, the API, the worker, or Gradle.
- A direct search of `settings.gradle` and `build.gradle` found no `site`, `npm`, or `node` coupling.

## Backend systems affected

The one-shot implementation affected `site/`, site-only CI, root README links, and progress/task documentation. Production Java source, API, worker, CLI runtime behavior, database, migrations, reports, metrics, packaging, and Docker configuration were not changed.

## Public product claims verified

Public site claims were checked against `apps/cli`, `apps/api`, `apps/worker`, `libs/executor`, `libs/reporting`, `README.md`, `docs/ci-integration.md`, `docs/runbook.md`, `docs/known-limitations.md`, and `examples/sample-api/backline.yml`. No customers, usage numbers, hosted-service behavior, dashboard functionality, or unsupported integrations are claimed.

## Known environment or platform limitations

- `$CODEX_HOME` was not set in the PowerShell process; automation memory is stored under the resolved local Codex home path instead.
- Local site browser verification uses installed Chromium and passed. The site CI workflow will install Chromium with Ubuntu dependencies.
- GitHub branch-protection settings were not required for the local implementation; PR #13 is the remote merge gate.
- Docker/Testcontainers-backed API and worker tests skipped locally during Phase 1.2; this shell did not prove Docker-backed integration behavior. Full E2E remains Phase 1.3.
- `./gradlew.bat check` could not run in this PowerShell session because the existing Gradle guardrail task invokes `/bin/bash`, unavailable through the local WSL bridge. `./gradlew.bat clean test` timed out while waiting on the local Docker/Testcontainers environment. The previously recorded `./gradlew.bat clean check` exit 0 and GitHub Ubuntu checks remain the authoritative backend evidence.
- Gradle 9 compatibility remains unverified because the current wrapper is Gradle 8.10.2 and reports deprecation warnings.

## GitHub actions taken

- Branch: created `codex/daily-roadmap-inventory` from the existing clean `main` checkout.
- Commit: `docs: establish daily roadmap inventory` (amended as the action record was finalized).
- Push: `codex/daily-roadmap-inventory` pushed to `origin`.
- Pull request: #13, `Establish daily roadmap inventory`.
- Phase 1.2 commit: `952c8d3` (`test: record Gradle baseline verification`) pushed to the existing PR branch.
- One-shot commits pushed to the existing PR branch: `3990c7f`, `3b2041e`, `d152ada`, `5f0d80f`, and `a103dea`.
- PR #13 remains the merge target; final CI/documentation commit and merge are pending.

## Deployment actions taken

- Vercel project: none; deployment was explicitly deferred because no canonical public host was selected.
- Deployment URL: none.
- Production status: not deployed; deployment is not relevant to Phase 1.1 or Phase 1.2.
- Rollback notes: not applicable.

## Blast radius

- Website source/build/CI: added under `site/` and `.github/workflows/site-ci.yml`.
- Gradle configuration, API, worker, CLI runtime, database, migrations, reports, metrics, and packaging: unaffected by the one-shot site implementation.
- Documentation: added the one-shot task contract, site README, root README site link, and progress evidence.
- GitHub: five implementation commits pushed to PR #13; merge remains pending final commit checks.
- Deployed site state: none; deployment remains explicitly deferred.
- Website isolation remains intact.
