# Backline Daily Roadmap Progress

Last updated: 2026-07-11 (America/Chicago)

## Current roadmap phase

Phase 1: Establish verified baselines.

## Ordered work-unit checklist

### Phase 1: Establish verified baselines

- [x] 1. Inventory repository modules, applications, libraries, workflows, migrations, Docker configuration, tests, performance tooling, and documentation.
- [ ] 2. Run and record existing Gradle verification.
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

## Current in-progress work unit

None. Phase 1.1 is complete. Next: Phase 1.2, run and record existing Gradle verification.

## Deferred items

- All website, deployment, backend hardening, observability, packaging, and reconciliation work remains ordered behind the Phase 1 baseline.
- No expensive E2E or performance command was run during the inventory-only work unit.

## Blockers

None identified for Phase 1.2.

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

## Verification results

- Targeted baseline: `.\\gradlew.bat projects` exited 0.
- Static cross-check: `settings.gradle` declares exactly eight Java subprojects, each with a `build.gradle` file.
- Inventory completeness was cross-checked against the root directory, workflow directory, migration directory, Dockerfiles, `perf/`, `scripts/`, and `docs/`.
- No tests were executed in this work unit; test execution and totals belong to Phase 1.2.
- Warnings: audit commands with scripting, capture, or timeout defects are described above. They did not mutate existing files, and affected claims were either rerun or explicitly not made.

## Architectural decisions

- Treat the existing Java module graph as mature baseline state; do not redesign it without a confirmed defect.
- Keep the future public website outside `settings.gradle` and all Java module build files.
- Use one roadmap work unit per automation run; this run created only the inventory and durable progress record.

## Website isolation evidence

- `site/` is absent.
- `settings.gradle` contains only the four application and four library modules listed above.
- No Gradle-to-Node or site build coupling was introduced.

## Backend systems affected

None. This work unit adds documentation only.

## Public product claims verified

No new public website claims were introduced. Repository inventory claims were verified directly from checked-in configuration and files.

## Known environment or platform limitations

- `$CODEX_HOME` was not set in the PowerShell process; automation memory is stored under the resolved local Codex home path instead.
- Testcontainers, Docker Compose runtime, E2E, browser, and performance behavior were not exercised in this inventory-only run.
- GitHub branch-protection and remote CI state were not needed for this local documentation work unit and remain unverified.

## GitHub actions taken

- Branch: created `codex/daily-roadmap-inventory` from the existing clean `main` checkout.
- Commit: `docs: establish daily roadmap inventory` (amended as the action record was finalized).
- Push: `codex/daily-roadmap-inventory` pushed to `origin`.
- Pull request: #13, `Establish daily roadmap inventory`.
- Merge: none.

## Deployment actions taken

- Vercel project: none.
- Deployment URL: none.
- Production status: not deployed; deployment is not relevant to Phase 1.1.
- Rollback notes: not applicable.

## Blast radius

- Website source/build/CI: unaffected.
- Gradle, API, worker, CLI, database, migrations, reports, metrics, and packaging: unaffected.
- Documentation: adds this progress record.
- GitHub and deployed site state: unaffected at the time of this record.
- Website isolation remains intact.
