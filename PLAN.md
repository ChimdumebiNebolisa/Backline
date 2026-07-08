# Backline Plan

## Purpose

This plan executes the full Backline build scope in a controlled sequence. The sequence is not a staged release model. Every step belongs to the same build scope.

Only one step may be active at a time. Do not move a step to done until its exit criteria are met.

## Status legend

```txt
BACKLOG
ACTIVE
BLOCKED
DONE
DROPPED
```

## Current status

```txt
ACTIVE: none (next: Q5)
BLOCKED: Q12 (PRD update)
DONE: Tasks 1-6, integration fixes, worker hardening, perf harness, PR #9 feature merge, PR #10 quality gates (Q1-Q4)
DROPPED: none
```

## 9/10 sequenced execution plan

Authoritative coordinator view for closing the remaining quality gaps. Does **not** expand PRD scope. Only one step (Q5–Q13, or Q14 sign-off) may be **ACTIVE** at a time.

### Baseline (post PR #10)

| Dimension | Current | Target (9.0) |
|-----------|---------|--------------|
| Overall audit score | ~8.3 | >= 9.0 |
| API line coverage | 25.9% (floor 28%) | >= 65% |
| CI full-stack proof | none | demo path green in Actions |
| CI skipped tests | 0 in CI | remain 0; local skips documented |
| Property / mutation tests | none | executor + config + policy + diff |
| Operability | runbook exists | doctor + policy profiles actionable |
| Enforced gates | `check` + guardrails | + E2E + ratcheting coverage |

### Gaps → steps

| # | Gap | Evidence | Step |
|---|-----|----------|------|
| G1 | No unattended full-stack proof | CI = `./gradlew check` only | Q5 |
| G2 | Silent skips locally | 54 skipped without Docker | Q8 |
| G3 | API under-tested | ~26% line vs 65% target | Q6 |
| G4 | Logic not stress-tested | no jqwik / PIT | Q9 |
| G5 | Security partially manual | guardrail script only | Q11 |
| G6 | Operability partial | basic doctor | Q7 |
| G7 | Coverage can regress | low API floor | Q10 |
| G8 | Perf blind spot | harness not in CI | Q13 |
| G9 | Diff UX (optional) | flag-only baselines | Q12 |

### Scope boundaries (phase-wide)

**In scope**

- Headless E2E demo in CI (Postgres → API → worker → sample API → CLI).
- Test pyramid to raise API coverage; JaCoCo floor ratcheting.
- jqwik property tests on existing executor, config, policy, diff logic.
- Doctor and policy profile hardening on existing CLI commands.
- Guardrail script expansion and redaction/preview automated tests.
- CI quality summary; perf **smoke** only (existing harness).
- Consolidating duplicate `PostgresTestBase` classes if touched during Q6/Q8.

**Out of scope** (do not implement; 9/10 achievable without these)

- Authentication / authorization.
- Multi-tenant isolation.
- Cloud sync / SaaS hosting.
- HTML report generation.
- OpenAPI-driven contract test generation.
- Full load testing as a product feature.
- New API endpoints or CLI commands beyond what each step lists.
- OWASP dependency check as a merge blocker (report-only optional).

**PRD-gated** (BLOCKED until `PRD.md` updated)

- Q12 persisted baseline preference (`backline baseline set/show`).

**Frozen constraints** (every step)

- CLI must not write to PostgreSQL.
- Worker must not expose HTTP.
- Reporting must not query the database directly.
- No shell execution from config.
- Response previews and sensitive-header redaction stay bounded per `GUARDRAILS.md`.

### Execution sequence (strict order)

```txt
Q5  E2E demo in CI
 └─> Q8  Zero skipped tests in CI
      └─> Q6  API coverage to 65% (Q6a → Q6b → Q6c → Q6d → Q6e)
           └─> Q9  Property + mutation tests
                └─> Q11  Security / redaction test expansion
                     └─> Q7  Policy profiles + doctor hardening
                          └─> Q10  Coverage ratchet + quality dashboard
                               └─> Q13  Performance smoke in CI
                                    └─> Q12  Baseline UX (optional; PRD-gated)
                                         └─> Q14  Re-audit sign-off
```

Do not start a step until all listed dependencies are **DONE**. Q6 must not begin until Q5 and Q8 are both DONE (proof layer complete).

### Master step tracker

| Order | Step | Status | Depends on | Closes gap | Verification artifact |
|-------|------|--------|------------|------------|------------------------|
| — | Q1 Contracts + runbook + guardrails | DONE | — | — | `docs/contracts.md`, `docs/runbook.md`, `check-guardrails.sh` |
| — | Q2 API test expansion (initial) | DONE | — | — | API line 25.9%; `:apps:api:test` green |
| — | Q3 ArchUnit enforcement | DONE | — | — | `ArchitectureTest` passes |
| — | Q4 CI coverage gates | DONE | — | — | `./gradlew check` + CI green |
| 1 | **Q5** E2E demo in CI | BACKLOG | Q4 | G1 | `./scripts/ci-e2e-demo.sh` + CI job green |
| 2 | **Q8** Zero skipped tests in CI | BACKLOG | Q5 | G2 | `CI=true ./gradlew check` → skipped=0 |
| 3 | **Q6** API coverage to 65% | BACKLOG | Q5, Q8 | G3 | API line >= 65%; floor 0.65 |
| 4 | **Q9** Property + mutation tests | BACKLOG | Q6 | G4 | jqwik tests green; PIT report-only OK |
| 5 | **Q11** Security / redaction tests | BACKLOG | Q9 | G5 | guardrails + executor tests green |
| 6 | **Q7** Policy profiles + doctor | BACKLOG | Q11 | G6 | CLI doctor/policy smoke tests |
| 7 | **Q10** Coverage ratchet + dashboard | BACKLOG | Q6, Q9 | G7 | floors match achieved; CI summary |
| 8 | **Q13** Perf smoke in CI | BACKLOG | Q10 | G8 | smoke job green (warn-only first merge OK) |
| 9 | **Q12** Baseline UX | BACKLOG | PRD | G9 | baseline set/show + diff default |
| 10 | **Q14** Re-audit sign-off | BACKLOG | Q5–Q11, Q13 | all | audit score >= 9.0 all dimensions |

Q12 may be **DROPPED** with reason if PRD is not updated; Q14 still requires Q5–Q11 and Q13 DONE.

### Per-step contract (summary)

Each step below expands: objective, in-scope artifacts, out-of-scope, dependencies, verification commands, exit criteria.

**Global verification** (run on every PR until Q14):

```bash
./gradlew check
./scripts/check-guardrails.sh
./scripts/audit-strength.sh
```

After Q5 lands, also run `./scripts/ci-e2e-demo.sh`. After Q8 lands, `CI=true ./gradlew clean check` must report `skipped=0`.

### Recommended PR slices

| PR | Step(s) | Example branch | Merge gate |
|----|---------|----------------|------------|
| A | Q5 | `cursor/e2e-ci-demo-ca88` | E2E script + CI job green |
| B | Q8 | `cursor/ci-zero-skips-ca88` | `CI=true` skipped=0 |
| C | Q6a–c | `cursor/api-coverage-services-ca88` | API line >= 40% |
| D | Q6d–e | `cursor/api-coverage-65-ca88` | API line >= 65%; floor 0.65 |
| E | Q9 | `cursor/property-tests-ca88` | jqwik green |
| F | Q11 | `cursor/security-redaction-tests-ca88` | guardrails + executor tests |
| G | Q7 | `cursor/policy-doctor-ca88` | doctor/policy smoke tests |
| H | Q10 | `cursor/coverage-ratchet-ca88` | floors + CI summary |
| I | Q13 | `cursor/perf-smoke-ci-ca88` | smoke job green |
| J | Q12 | `cursor/baseline-ux-ca88` | blocked until PRD update |

### Risks

| Risk | Mitigation |
|------|------------|
| E2E flake | Health waits + timeouts; one CI retry; upload service logs |
| Q6 scope creep | Coverage gap table only; no new endpoints |
| Duplicate `PostgresTestBase` | Consolidate during Q6a or Q8 |
| PIT slows CI | Report-only until baseline; optional nightly |
| Q12 without PRD | DROPPED; does not block Q14 |

### Immediate next action

**Activate Q5.** See step detail below. Do not activate Q6 until Q5 and Q8 are DONE.

---

### Q5 — E2E demo in CI

**Objective:** Prove Postgres → API → worker → sample API → CLI path unattended in GitHub Actions.

**Depends on:** Q4 (DONE).

**In scope:**
- `scripts/ci-e2e-demo.sh` (headless runner derived from `docs/demo-script.md`)
- `.github/workflows/backline-ci.yml` — `e2e-demo` job (or extend `verify`)
- Health wait loops for API (8080), sample API (8081), Postgres (5432)
- CLI flow: `doctor`, `sample init`, `run`, `history`, `diff`, `report`
- CI artifacts: generated report.md + JSON
- Optional: `EndToEndDemoTest.java` JUnit wrapper invoking script assertions

**Out of scope:**
- Docker Compose as required CI path (prefer service container or script-managed Postgres)
- New CLI commands or API endpoints
- Parallel test matrix / multi-OS E2E

**Must not:** Bypass CLI→API boundary; CLI must not write to Postgres directly.

**Exit criteria:**
1. `./scripts/ci-e2e-demo.sh` exits 0 locally on Ubuntu with Java 21.
2. CI `e2e-demo` job green on PR to `main`.
3. Run reaches terminal status; report file exists; run ID printed.

**Verification:**
```bash
./scripts/ci-e2e-demo.sh
# CI: e2e-demo job green on PR
```

---

### Q6 — API coverage to 65%

**Objective:** Raise `apps/api` line coverage from 25.9% to >= 65% with a test pyramid, not controller-only sprawl.

**Depends on:** Q5, Q8 (both DONE).

**In scope:**

| Layer | Target classes | Test type |
|-------|----------------|-----------|
| Services | `RunService`, `DiffService`, `CheckSyncService`, `CheckHistoryService`, `ProjectSummaryService`, `ProjectService` | `@SpringBootTest` + PostgresTestBase |
| Web | `ApiExceptionHandler`, all controllers | `@WebMvcTest` or TestRestTemplate integration |
| Persistence | custom repository queries, `RunSpecifications`, `OffsetBasedPageRequest` | repository integration tests |
| Mappers | `*Mapper`, `AssertionJsonMapper` | unit tests |
| Config | `JacksonConfig`, `OpenApiConfig` | smoke/unit |

**Sub-steps (strict order within Q6):**
1. **Q6a** — Service tests: cancel, events, error branches.
2. **Q6b** — Controller contract matrix: every endpoint × success / validation / not-found.
3. **Q6c** — Repository query tests (`findPreviousPassedRun`, history projection, filters).
4. **Q6d** — Mapper/unit tests for JSON serialization edges (assertion operators).
5. **Q6e** — Raise JaCoCo floor 0.28 → 0.40 → 0.55 → 0.65 (one bump per sub-step merge).

**Out of scope:**
- New API endpoints or DTO shape changes
- Refactoring production services for testability beyond minimal seams
- Worker or CLI test expansion (covered in other steps)

**Exit criteria:**
1. `./gradlew :apps:api:jacocoTestCoverageVerification` passes at floor >= 0.65.
2. `./scripts/audit-strength.sh` reports `apps/api` line >= 65%.
3. `./scripts/ci-e2e-demo.sh` still green (no demo regression).

**Verification:**
```bash
./gradlew :apps:api:test jacocoTestCoverageVerification
./scripts/audit-strength.sh  # apps/api line >= 65%
./scripts/ci-e2e-demo.sh     # after Q5
```

---

### Q7 — Policy profiles + doctor hardening

**Objective:** Make CI gating and local troubleshooting first-class without expanding into a general CI platform.

**Depends on:** Q11 (DONE).

**In scope:**
- Named policy presets in `backline.yml` (`strict`, `warn-only`) or `RunPolicyProfile` enum
- CLI: `backline run --policy strict` mapping to existing `RunPolicyEvaluator`
- Enhanced `backline doctor`: API, config validity, optional sample API; non-zero exit on critical failure
- CLI smoke tests for each doctor failure mode
- Updates to `docs/ci-integration.md` and `docs/runbook.md`
- Small PRD addition documenting named policy presets (thresholds already exist)

**Out of scope:**
- General CI platform / pipeline orchestration
- Remote worker health polling beyond documented doctor checks
- New persistence tables

**Exit criteria:**
1. `./gradlew :apps:cli:test` green including doctor/policy smoke tests.
2. `backline doctor` exits 1 with actionable message when API unreachable.
3. `backline run --policy strict --enforce-policy` exit code documented in runbook.

**Verification:**
```bash
./gradlew :apps:cli:test
backline doctor  # actionable output, exit 1 when API down
backline run --policy strict --enforce-policy  # exit code documented
```

---

### Q8 — Zero skipped tests in CI

**Objective:** CI must never silently skip integration tests; local dev may skip when Docker absent.

**Depends on:** Q5 (DONE).

**In scope:**
- Audit all `Assumptions.assumeTrue` / `@EnabledIf` in test bases (`PostgresTestBase`, `PostgresWorkerTestBase`, etc.)
- When `CI=true`, missing Docker → test failure (not skip)
- CI workflow sets `CI=true` explicitly (already present — verify)
- `scripts/audit-strength.sh` exits non-zero if `skipped > 0` when `CI=true`
- Consolidate duplicate `PostgresTestBase` under `persistence/` and `support/` if not done in Q6
- Document local skip behavior in `docs/runbook.md`

**Out of scope:**
- Requiring Docker for local `./gradlew test` (skips OK locally without `CI=true`)
- Replacing Testcontainers with embedded Postgres

**Exit criteria:**
1. `CI=true ./gradlew clean check` completes with `skipped=0` in JUnit summary.
2. `./scripts/audit-strength.sh` fails when run with `CI=true` and any skip occurs.
3. Local run without Docker still skips gracefully (no regression for dev UX).

**Verification:**
```bash
CI=true ./gradlew clean check
CI=true ./scripts/audit-strength.sh
# audit output: skipped=0
```

---

### Q9 — Property + mutation tests

**Objective:** Prove critical logic is not just covered but meaningfully tested.

**Depends on:** Q6 (DONE).

**In scope:**
- jqwik property tests in `libs/executor` (assertion evaluation against generated JSON)
- jqwik property tests in `libs/config` (malformed config rejection)
- Boundary tests for `RunPolicyEvaluator` (threshold edges)
- Property or generative tests for `DiffService` baseline selection edge cases
- Optional PIT mutation task for `libs/executor` and `DiffService` (Gradle plugin, report-only)
- Document acceptable mutation survivors in test class comments

**Out of scope:**
- PIT as merge blocker until baseline established
- Property tests on controllers or persistence layers
- Fuzzing live HTTP endpoints

**Must not:** Add mutation testing as a merge blocker until baseline established.

**Exit criteria:**
1. `./gradlew :libs:executor:test :libs:config:test :apps:cli:test :apps:api:test` green.
2. At least one jqwik test class per module: executor, config, policy evaluator, diff baselines.
3. Optional `./gradlew pitest` produces report without blocking merge.

**Verification:**
```bash
./gradlew :libs:executor:test :libs:config:test :apps:cli:test :apps:api:test
# optional: ./gradlew pitest (threshold configured)
```

---

### Q10 — Coverage ratchet + quality dashboard

**Objective:** Prevent coverage regression; make quality visible to reviewers.

**Depends on:** Q6, Q9 (both DONE).

**In scope:**
- Raise `coverageMinimums` in `build.gradle` to match achieved coverage (all modules, not only API)
- CI step publishes coverage summary table as artifact or job summary
- README quality snapshot section updated by `audit-strength.sh` or documented manual refresh
- Optional Shields.io badge if repo is public

**Out of scope:**
- SonarQube / external quality platform integration
- Per-class coverage enforcement
- Blocking PR merge on coverage delta comments

**Exit criteria:**
1. `./gradlew check` fails if any module drops below its ratcheted floor.
2. CI publishes per-module line coverage summary on every PR.
3. README or `docs/audit-playbook.md` documents how to read coverage artifacts.

**Verification:**
```bash
./gradlew check  # fails if coverage drops below floor
```

---

### Q11 — Security/redaction test expansion

**Objective:** Automate guardrails that are today partially manual.

**Depends on:** Q9 (DONE).

**In scope:**
- Unit tests: `Authorization`, `Cookie`, `Set-Cookie` redacted in executor logs/previews
- URL validation tests for documented dangerous schemes
- Response preview bound tests (extend existing)
- Expand `scripts/check-guardrails.sh`:
  - preview constant matches docs
  - grep for `Runtime.exec` / `ProcessBuilder` in config paths
  - verify no `@SpringBootApplication` in CLI main
- Optional OWASP dependency check Gradle task (report-only on PR)

**Out of scope:**
- Penetration testing / DAST
- Secret scanning as merge blocker
- Auth implementation

**Exit criteria:**
1. `./scripts/check-guardrails.sh` passes with expanded checks.
2. `./gradlew :libs:executor:test` green including new redaction/preview tests.
3. Guardrail additions documented in `GUARDRAILS.md` or `docs/runbook.md`.

**Verification:**
```bash
./scripts/check-guardrails.sh
./gradlew :libs:executor:test
```

---

### Q12 — Baseline UX commands (PRD-gated)

**Objective:** Improve diff ergonomics for daily use.

**Depends on:** `PRD.md` update (BLOCKED until approved).

**In scope (after PRD update):**
- API: store baseline run preference per project/environment
- CLI: `backline baseline set <runId>`, `backline baseline show`
- Diff defaults to stored baseline when no flag provided
- Tests: API + CLI + diff integration

**Out of scope:**
- Multiple named baselines per project
- Baseline sharing across machines / teams
- UI beyond CLI

**Exit criteria:**
1. PRD lists baseline persistence as in-scope.
2. `./gradlew :apps:api:test :apps:cli:test` green.
3. `backline baseline set <id> && backline diff <newRunId>` uses stored baseline.

**Verification:**
```bash
./gradlew :apps:api:test :apps:cli:test
backline baseline set <id> && backline diff <newRunId>  # uses stored baseline
```

If PRD is not updated by Q14, mark **DROPPED** with reason; does not block sign-off.

---

### Q13 — Performance smoke in CI

**Objective:** Catch gross regressions in worker throughput / queue latency using existing harness.

**Depends on:** Q10 (DONE).

**In scope:**
- Wire `perf/scripts/` smoke profile into CI (post-`verify` job or nightly on `main`)
- Document thresholds in `docs/runbook.md`
- Fail CI only on smoke profile breach after baseline established (warn-only on first merge)

**Out of scope:**
- Full load test profiles in PR CI
- Performance optimization work
- New perf metrics product surface

**Exit criteria:**
1. CI smoke job runs existing harness without modifying worker claim semantics.
2. Thresholds documented; first merge may warn-only.
3. `./scripts/ci-e2e-demo.sh` and `./gradlew check` remain green.

**Verification:**
```bash
# local (documented equivalent)
./perf/scripts/queue-load.ps1 -Profile smoke
# CI: perf-smoke job green or warn-only on first merge
```

---

### Q14 — Re-audit sign-off

**Objective:** Confirm 9/10 quality bar with repeatable evidence.

**Depends on:** Q5–Q11, Q13 DONE; Q12 DONE or DROPPED.

**In scope:**
- Run `./scripts/audit-strength.sh` and record scores in `docs/audit-playbook.md` or README
- Update `PLAN.md` step statuses to DONE
- Confirm all verification commands pass on `main`

**Exit criteria (definition of done for 9/10):**

1. `./gradlew check` passes locally and in CI with `skipped=0`.
2. `./scripts/ci-e2e-demo.sh` passes in CI.
3. API line coverage >= 65%.
4. Property tests cover executor assertion evaluation and config validation.
5. Doctor and policy profiles documented and smoke-tested.
6. Guardrail script covers security-sensitive paths with automated tests.
7. Q5–Q11 and Q13 marked DONE in this plan; Q12 DONE or DROPPED with reason.
8. Re-audit score >= 9.0 on all tracked dimensions.

**Verification:**
```bash
./gradlew clean check
CI=true ./scripts/audit-strength.sh
./scripts/ci-e2e-demo.sh
./scripts/check-guardrails.sh
```

## Parallel Execution Map

The 12 sequential steps below define the canonical build scope. To execute the build in parallel without violating ownership or guardrails, the work is partitioned into six non-overlapping workstreams. Each workstream maps to one or more of the sequential steps.

### Workstream mapping

```txt
Task 1: skeleton + libs/core + dependency setup       -> Step 1 + shared bits of Step 2
Task 2: migrations + persistence (entities/repos)     -> Step 2
Task 3: API controllers, services, validation, docs   -> Steps 3, 4, 8
Task 4: config parser + CLI shell + API client        -> Steps 5, 7
Task 5: worker + libs/executor                        -> Step 6
Task 6: sample API + reporting + docker + docs        -> Steps 9, 10, 11, 12 (final docs)
```

### File ownership matrix (authoritative)

```txt
Task 1 owns:
  settings.gradle
  build.gradle (root)
  gradle/ (wrapper) -- if regenerated
  gradlew, gradlew.bat
  .gitignore
  .env.example (initial stub)
  apps/cli/build.gradle (module skeleton only, no commands)
  apps/api/build.gradle (module skeleton only)
  apps/worker/build.gradle (module skeleton only)
  apps/sample-api/build.gradle (module skeleton only)
  libs/core/**
  libs/config/build.gradle (module skeleton only)
  libs/executor/build.gradle (module skeleton only)
  libs/reporting/build.gradle (module skeleton only)
  README.md (skeleton header only)
  PLAN.md updates only via the coordinator

Task 2 owns:
  db/migration/**
  apps/api/src/main/java/.../persistence/entity/**
  apps/api/src/main/java/.../persistence/repository/**
  apps/api/src/main/resources/application*.yml (database/Flyway config only;
    Task 3 may add API web config separately)
  apps/api/src/test/java/.../migration/**
  apps/api/src/test/java/.../persistence/**

Task 3 owns:
  apps/api/src/main/java/.../ApiApplication.java
  apps/api/src/main/java/.../controller/**
  apps/api/src/main/java/.../service/**
  apps/api/src/main/java/.../dto/**          (API-local request/response shapes;
                                              shared cross-module DTOs stay in libs/core)
  apps/api/src/main/java/.../exception/**
  apps/api/src/main/java/.../config/**       (web/OpenAPI/Actuator config)
  apps/api/src/main/java/.../web/**          (filters, error handlers)
  apps/api/src/test/java/.../api/**
  apps/api/src/test/java/.../controller/**
  apps/api/src/test/java/.../service/**

Task 4 owns:
  libs/config/**
  apps/cli/**
  apps/cli/src/test/**
  NOTE: examples/sample-api/backline.yml is owned by Task 6.
        Task 4's `backline init` / `backline sample init` commands write
        the same file content into the project's working directory at
        runtime, but the canonical authored file lives under
        examples/sample-api/ owned by Task 6.

Task 5 owns:
  apps/worker/**
  apps/worker/src/main/resources/application*.yml
  libs/executor/**
  apps/worker/src/test/**
  libs/executor/src/test/**

Task 6 owns:
  apps/sample-api/**
  libs/reporting/**
  examples/sample-api/**
  docs/api-examples.md
  docs/known-limitations.md
  docs/demo-script.md
  Dockerfiles: apps/api/Dockerfile, apps/worker/Dockerfile, apps/sample-api/Dockerfile (if added)
  docker-compose.yml
  README.md (final content sections; Task 1 leaves only a skeleton header)
```

### Cross-task contracts (do not violate)

1. Shared domain types (enums, error codes, response wrappers, cross-module DTOs)
   live in `libs/core`. Task 1 publishes them first. Tasks 2-6 must consume
   these types instead of redefining them.
2. Persistence:
   - JPA entities + Spring Data repositories used by the API live in
     `apps/api/.../persistence/**` (Task 2).
   - Worker-specific data access (claim with `FOR UPDATE SKIP LOCKED`,
     result inserts, finalize transitions, worker run events) lives in
     `apps/worker/.../persistence/**` using JdbcTemplate (Task 5).
   - This is not duplicated business logic. The shared contract is the
     migration schema, not Java types.
3. The CLI must call API endpoints for all persistence (Task 4 must not write
   to PostgreSQL; the API client lives in `apps/cli/.../client/**`).
4. The worker must not expose HTTP endpoints. It only polls the database.
5. The report generator (`libs/reporting`) must consume the API client
   response types from `libs/core` (and a small read-only client surface
   Task 4 exposes); it must not query the database directly.
6. `application.yml` files: Task 2 owns DB/Flyway sections, Task 3 owns
   web/OpenAPI/Actuator sections, Task 5 owns worker-specific config. They
   must namespace their keys so merges are conflict-free.

### Bootstrap gate

Task 1 runs first to establish module skeletons (settings.gradle, root
build.gradle, per-module build files, libs/core domain types).

After Task 1 completes its exit criteria (`./gradlew build` succeeds with
all modules visible), Tasks 2-6 may run in parallel.

### Integration pass

After Tasks 2-6 finish, a single integration agent:
- Resolves compile or wiring issues between modules.
- Runs the full verification matrix.
- Reconciles `docker-compose.yml` with actual module names and ports.
- Updates README only if commands shifted.
- Confirms no guardrail violations remain.

### Status tracking

```txt
Task 1 (skeleton):                        DONE
Task 2 (db/persistence):                  DONE
Task 3 (api):                             DONE
Task 4 (config/cli):                      DONE
Task 5 (worker/executor):                 DONE
Task 6 (sample/report/docker/docs):       DONE
Integration pass:                         DONE
Worker hardening:                         DONE (stale recovery, timeout, cancellation, structured logs)
Perf harness:                             DONE (smoke, small, multi-worker profiles)
```

### Frozen contracts (Tasks 2-6 must consume as-is)

These contracts are pinned by the coordinator to enable parallel work. Do not deviate.

**Java package roots:**
- `dev.backline.core` -> libs/core (already published)
- `dev.backline.config` -> libs/config
- `dev.backline.executor` -> libs/executor
- `dev.backline.reporting` -> libs/reporting
- `dev.backline.api` -> apps/api
- `dev.backline.worker` -> apps/worker
- `dev.backline.sample` -> apps/sample-api
- `dev.backline.cli` -> apps/cli

**Database table names (per ARCHITECTURE.md):** `projects`, `checks`, `runs`, `check_results`, `run_events`.

**Primary keys:** UUID (`uuid` Postgres type, `gen_random_uuid()` default).
Requires `CREATE EXTENSION IF NOT EXISTS pgcrypto;` in V1.

**Entity class names (Task 2 publishes; Task 3 consumes):**
- `dev.backline.api.persistence.entity.ProjectEntity`
- `dev.backline.api.persistence.entity.CheckEntity`
- `dev.backline.api.persistence.entity.RunEntity`
- `dev.backline.api.persistence.entity.CheckResultEntity`
- `dev.backline.api.persistence.entity.RunEventEntity`

**Repository interface names (Task 2 publishes; Task 3 consumes):**
- `dev.backline.api.persistence.repository.ProjectRepository`
- `dev.backline.api.persistence.repository.CheckRepository`
- `dev.backline.api.persistence.repository.RunRepository`
- `dev.backline.api.persistence.repository.CheckResultRepository`
- `dev.backline.api.persistence.repository.RunEventRepository`

**Application port:** API on 8080, sample API on 8081.

**application.yml ownership inside apps/api:** Task 2 owns the file fully
(datasource, JPA, Flyway, server.port=8080, basic management). Task 3 does
NOT touch the YAML. OpenAPI / extra config goes in Java @Configuration.

**Sample backline.yml canonical content:** Identical wherever it appears
(Task 6's `examples/sample-api/backline.yml` and Task 4's CLI template
resource). Source of truth is PRD.md "Config file" section.

**Run claim SQL (Task 5 implements; Task 2 must enable):**
```sql
SELECT id FROM runs
 WHERE status = 'QUEUED' AND next_attempt_at <= now()
 ORDER BY queued_at ASC
 FOR UPDATE SKIP LOCKED LIMIT 1;
```
Task 2 must ensure `next_attempt_at` defaults to `now()` so a freshly
inserted QUEUED run is immediately claimable.

## Step 1: Project skeleton and build system

Status: ACTIVE

Objective:

Create the repository structure, Gradle multi-project build, Java 21 configuration, base packages, and empty modules.

Dependencies:

- PRD approved.
- Architecture approved.
- Guardrails approved.

Artifacts to create or edit:

```txt
settings.gradle
build.gradle
apps/cli/
apps/api/
apps/worker/
apps/sample-api/
libs/core/
libs/config/
libs/executor/
libs/reporting/
.env.example
docker-compose.yml
README.md
```

Expected output:

- Project imports cleanly.
- All modules compile with empty or minimal code.
- Java 21 is enforced.

Verification type:

- Build verification.

Verification method:

```bash
./gradlew clean build
```

Exit criteria:

- Build passes.
- All modules are visible in Gradle.
- No application behavior is implemented yet.

Must not change:

- PRD scope.
- Architecture ownership.
- Command list.

## Step 2: Database migrations and core domain

Status: BACKLOG

Objective:

Create database schema, core enums, domain records, and migration tests.

Dependencies:

- Step 1 done.

Artifacts to create or edit:

```txt
db/migration/V1__create_projects.sql
db/migration/V2__create_checks.sql
db/migration/V3__create_runs.sql
db/migration/V4__create_check_results.sql
db/migration/V5__create_run_events.sql
db/migration/V6__add_indexes.sql
libs/core/src/main/java/...
apps/api/src/test/java/.../MigrationTest.java
```

Expected output:

- Empty PostgreSQL database migrates successfully.
- Core statuses and DTOs exist.
- Constraints and indexes exist.

Verification type:

- Integration test with PostgreSQL.

Verification method:

```bash
./gradlew :apps:api:test
```

Exit criteria:

- Migrations run from empty database.
- Status constraints reject invalid values.
- Unique constraints are covered by tests.

Must not change:

- Entity ownership.
- Run statuses.
- Database as durable source of truth.

## Step 3: API foundation, validation, OpenAPI, and Actuator

Status: BACKLOG

Objective:

Implement API server foundation with health checks, structured errors, validation, and OpenAPI docs.

Dependencies:

- Step 2 done.

Artifacts to create or edit:

```txt
apps/api/src/main/java/.../ApiApplication.java
apps/api/src/main/java/.../controller/
apps/api/src/main/java/.../exception/
apps/api/src/main/java/.../config/
apps/api/src/test/java/.../HealthAndErrorTest.java
```

Expected output:

- API starts.
- `/actuator/health` works.
- `/api/health` works.
- Validation errors use the documented shape.
- OpenAPI docs are available.

Verification type:

- API integration test.

Verification method:

```bash
./gradlew :apps:api:test
curl http://localhost:8080/actuator/health
```

Exit criteria:

- Health endpoints return valid responses.
- Invalid request returns structured error.
- OpenAPI endpoint loads.

Must not change:

- API response shape.
- Actuator inclusion.
- Structured error contract.

## Step 4: Project, check sync, and run submission API

Status: BACKLOG

Objective:

Implement project creation, check sync, and queued run creation.

Dependencies:

- Step 3 done.

Artifacts to create or edit:

```txt
apps/api/src/main/java/.../controller/ProjectController.java
apps/api/src/main/java/.../controller/CheckController.java
apps/api/src/main/java/.../controller/RunController.java
apps/api/src/main/java/.../service/
apps/api/src/main/java/.../repository/
apps/api/src/test/java/.../RunSubmissionTest.java
```

Expected output:

- Projects can be created.
- Checks can be synced by stable key.
- Runs can be created in `QUEUED` status.
- Run events are written.

Verification type:

- API integration test.

Verification method:

```bash
./gradlew :apps:api:test
```

Exit criteria:

- `POST /api/runs` creates a queued run.
- Duplicate submission behavior is deterministic.
- Check sync updates current check definitions safely.

Must not change:

- Worker ownership of execution.
- CLI must still not write directly to database.

## Step 5: Config parser and validation

Status: BACKLOG

Objective:

Implement `backline.yml` parsing and deterministic validation.

Dependencies:

- Step 4 done.

Artifacts to create or edit:

```txt
libs/config/src/main/java/...
libs/config/src/test/java/...
examples/sample-api/backline.yml
```

Expected output:

- Valid config parses.
- Invalid config fails with actionable errors.
- Duplicate check keys are rejected.

Verification type:

- Unit tests.

Verification method:

```bash
./gradlew :libs:config:test
```

Exit criteria:

- Parser supports required config shape.
- Validation covers required fields, URLs, methods, status codes, latency, and assertions.

Must not change:

- Config schema without PRD update.
- Assertion scope.

## Step 6: Worker claim, execution, and result finalization

Status: BACKLOG

Objective:

Implement worker job claim, HTTP execution, assertion evaluation, result storage, and final run status.

Dependencies:

- Step 5 done.

Artifacts to create or edit:

```txt
apps/worker/src/main/java/...
libs/executor/src/main/java/...
apps/worker/src/test/java/.../WorkerClaimTest.java
apps/worker/src/test/java/.../WorkerExecutionTest.java
```

Expected output:

- Worker claims queued runs safely.
- Worker executes checks.
- Worker stores results.
- Worker finalizes runs.
- Multiple workers do not process the same run.

Verification type:

- Integration test with PostgreSQL and local test HTTP server.

Verification method:

```bash
./gradlew :apps:worker:test
```

Exit criteria:

- Queued run becomes running, then passed, failed, or error.
- Results are written exactly once.
- Concurrency test proves no double-processing.

Must not change:

- CLI command behavior.
- API response contracts.
- Database status model.

## Step 7: CLI commands

Status: BACKLOG

Objective:

Implement Picocli command surface and API client behavior.

Dependencies:

- Step 6 done.

Artifacts to create or edit:

```txt
apps/cli/src/main/java/.../BacklineCommand.java
apps/cli/src/main/java/.../commands/
apps/cli/src/test/java/.../CliSmokeTest.java
```

Expected output:

The CLI supports:

```bash
backline init
backline run
backline run --no-wait
backline status <runId>
backline history
backline diff <runId>
backline report <runId>
backline worker
backline doctor
```

Verification type:

- CLI smoke tests.

Verification method:

```bash
./gradlew :apps:cli:test
```

Exit criteria:

- Each command parses correctly.
- Commands return useful exit codes.
- `backline run` prints a run ID.
- `backline doctor` checks config and API connectivity.

Must not change:

- CLI must not write to database.
- CLI must not execute production checks.

## Step 8: History, filtering, pagination, diff, and summary

Status: BACKLOG

Objective:

Implement query endpoints and diff calculation.

Dependencies:

- Step 7 done.

Artifacts to create or edit:

```txt
apps/api/src/main/java/.../controller/HistoryController.java
apps/api/src/main/java/.../service/DiffService.java
apps/api/src/test/java/.../RunHistoryAndDiffTest.java
```

Expected output:

- Runs can be listed with pagination.
- Runs can be filtered.
- Run results can be fetched.
- Diff can compare selected run to previous completed run.
- Project summary can be fetched.

Verification type:

- API integration tests.

Verification method:

```bash
./gradlew :apps:api:test
```

Exit criteria:

- Filters work.
- Pagination metadata is correct.
- Diff identifies new failures, fixed failures, status changes, and latency changes.

Must not change:

- Stored result data.
- Worker execution logic.

## Step 9: Sample API and sample commands

Status: BACKLOG

Objective:

Implement fast demo path with sample config and sample server.

Dependencies:

- Step 8 done.

Artifacts to create or edit:

```txt
apps/sample-api/src/main/java/...
apps/cli/src/main/java/.../commands/SampleInitCommand.java
apps/cli/src/main/java/.../commands/SampleServeCommand.java
examples/sample-api/backline.yml
examples/sample-api/README.md
apps/cli/src/test/java/.../SampleCommandTest.java
```

Expected output:

- `backline sample init` creates sample files.
- `backline sample serve` starts local sample API.
- Sample API has passing and failing endpoints.

Verification type:

- CLI and sample API test.

Verification method:

```bash
./gradlew :apps:sample-api:test :apps:cli:test
```

Exit criteria:

- Sample files are created without overwriting unexpectedly.
- Sample API endpoints respond as documented.
- Sample config runs against sample API.

Must not change:

- Production API behavior.
- Worker ownership of execution.

## Step 10: Report generation

Status: BACKLOG

Objective:

Generate Markdown reports from stored run data.

Dependencies:

- Step 9 done.

Artifacts to create or edit:

```txt
libs/reporting/src/main/java/...
apps/cli/src/main/java/.../commands/ReportCommand.java
libs/reporting/src/test/java/.../MarkdownReportTest.java
```

Expected output:

- `backline report <runId>` writes a Markdown file.
- Report includes summary, failures, latency, diff, limitations, and timestamp.

Verification type:

- Unit test and CLI smoke test.

Verification method:

```bash
./gradlew :libs:reporting:test :apps:cli:test
```

Exit criteria:

- Report file is generated.
- Report content matches required sections.
- CLI prints generated file path.

Must not change:

- API data contract.
- Direct database access prohibition.

## Step 11: Docker Compose and local runtime

Status: BACKLOG

Objective:

Make the full system runnable locally with one Docker Compose command.

Dependencies:

- Step 10 done.

Artifacts to create or edit:

```txt
docker-compose.yml
.env.example
README.md
apps/api/Dockerfile
apps/worker/Dockerfile
```

Expected output:

- PostgreSQL starts.
- API starts.
- Worker starts.
- Health check works.
- CLI can connect to local API.

Verification type:

- Docker runtime check.

Verification method:

```bash
docker compose up --build
curl http://localhost:8080/actuator/health
```

Exit criteria:

- API and worker are running.
- API health is UP.
- Database health is visible.
- Worker connects to database.

Must not change:

- Local-first assumption.
- No SaaS dependency.

## Step 12: End-to-end verification and README

Status: BACKLOG

Objective:

Verify the reviewer demo path and document the project.

Dependencies:

- Step 11 done.

Artifacts to create or edit:

```txt
README.md
docs/api-examples.md
docs/known-limitations.md
docs/demo-script.md
```

Expected output:

A reviewer can run:

```bash
docker compose up --build
backline sample init
backline sample serve
backline run
backline history
backline diff <runId>
backline report <runId>
```

Verification type:

- Manual end-to-end check plus recorded CLI output.

Verification method:

```bash
./gradlew clean test
docker compose up --build
backline sample init
backline sample serve
backline run
backline history
backline diff <runId>
backline report <runId>
```

Exit criteria:

- All commands work as documented.
- README includes setup, run, test, demo, architecture, limitations, and troubleshooting.
- Known limitations are explicit.
- No guardrail violations remain.

Must not change:

- Scope.
- Architecture.
- Core command names.

## Project definition of done

The build is done only when:

- All must-have PRD items are complete.
- All plan steps are done or explicitly dropped with a reason.
- Core flows have verification artifacts.
- Active blockers are resolved or explicitly accepted.
- No unresolved guardrail violations remain.
- The delivered system matches `PRD.md` and `ARCHITECTURE.md`.
