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
BLOCKED: none
DONE: Tasks 1-6, integration fixes, worker hardening, perf harness, PR #9 feature merge, PR #10 quality gates (Q1-Q4)
DROPPED: none
```

## Quality roadmap — execution architecture

This section is the coordinator view for reaching **9/10** from the current **~8.3/10** baseline. It does not change PRD scope; it sequences proof, coverage, and operability work already implied by guardrails and audit findings.

### What still blocks 9/10

| Gap | Evidence today | Target | Primary step |
|-----|----------------|--------|--------------|
| No unattended full-stack proof | CI runs `./gradlew check` only | Demo path green in Actions | Q5 |
| API coverage far below bar | ~26% line (floor 28%) | >= 65% line | Q6 |
| Local skips mask missing Docker | 54 skipped without Docker | CI `skipped=0`; local skips documented | Q8 |
| Critical logic under-tested | No jqwik/PIT | Property tests on executor/config/policy/diff | Q9 |
| Operability partial | Runbook exists; doctor basic | Policy profiles + actionable doctor | Q7 |
| Security checks partial | Guardrail script exists | Redaction + preview bound tests expanded | Q11 |
| Coverage can regress silently | Floors low on API | Ratchet + audit summary | Q10 |
| Diff UX (optional) | Baseline flags only | Persisted baseline preference | Q12 (PRD-gated) |
| Perf regression blind spot | Harness exists, not in CI | Smoke profile on main/nightly | Q13 |

Out of scope for 9/10 (unless PRD updated): auth, multi-tenant, SaaS, HTML reports, OpenAPI contract generation.

### Milestones (sequential gates)

```txt
M1  Proof layer     Q5 + Q8   — E2E demo in CI; zero skips when CI=true
M2  Depth layer     Q6        — API coverage pyramid to 65%; floors ratcheted
M3  Rigor layer     Q9 + Q11  — Property tests + security/redaction expansion
M4  Operability     Q7        — Policy profiles + doctor hardening
M5  Sustainability  Q10       — Coverage ratchet + quality dashboard in CI/README
M6  Optional polish Q12 + Q13 — Baseline UX (PRD) + perf smoke (non-blocking first)
M7  Sign-off        Re-audit  — audit-playbook score >= 9.0 on all tracked dimensions
```

Only one **plan step** (Q5–Q13) may be ACTIVE at a time. Milestones can complete across multiple merges but must not start the next milestone’s **first** step until the prior milestone’s exit criteria pass.

### Recommended PR slices

Each slice = one branch, one PR, one verification artifact. Merge order is fixed.

| PR | Steps | Branch suffix (example) | Merge gate |
|----|-------|-------------------------|------------|
| PR-A | Q5 | `cursor/e2e-ci-demo-ca88` | `./scripts/ci-e2e-demo.sh` green locally + CI job green |
| PR-B | Q8 | `cursor/ci-zero-skips-ca88` | `CI=true ./gradlew check` → skipped=0 |
| PR-C | Q6a–Q6c | `cursor/api-coverage-services-ca88` | API line >= 40%; `./gradlew check` |
| PR-D | Q6d–Q6e | `cursor/api-coverage-65-ca88` | API line >= 65%; floor 0.65 in `build.gradle` |
| PR-E | Q9 | `cursor/property-tests-ca88` | jqwik tests green; PIT report-only if added |
| PR-F | Q11 | `cursor/security-redaction-tests-ca88` | `./scripts/check-guardrails.sh` + executor tests |
| PR-G | Q7 | `cursor/policy-doctor-ca88` | CLI doctor/policy smoke tests |
| PR-H | Q10 | `cursor/coverage-ratchet-ca88` | Floors match achieved coverage; CI summary |
| PR-I | Q13 | `cursor/perf-smoke-ci-ca88` | Perf smoke job green (warn-only first merge OK) |
| PR-J | Q12 | `cursor/baseline-ux-ca88` | **Blocked until PRD.md updated** |

PR-C and PR-D are the largest diffs (many test files). Split Q6 by layer (services → controllers → repos → mappers) rather than by endpoint to avoid merge conflicts in shared test fixtures.

### Parallelization rules (within a step)

While only one Q-step is ACTIVE, sub-agents may work in parallel **inside** that step when file ownership does not overlap:

- **Q6:** Service tests (Task 3 ownership) parallel to mapper unit tests (Task 3); no controller + service edits to the same test fixture in one PR without coordinator merge.
- **Q9:** `libs/executor` and `libs/config` property tests in parallel; `RunPolicyEvaluator` (CLI) and `DiffService` (API) sequential or separate PRs if conflict-prone.
- **Q5:** Script author + workflow author can pair; single PR merges both.

Do **not** parallelize Q5 and Q6 on the same branch — E2E must land first so later API changes do not break the demo path silently.

### Risks and mitigations

| Risk | Mitigation |
|------|------------|
| E2E flake (port races, slow boot) | Health wait loops with timeouts; retry once in CI; artifact logs on failure |
| Q6 scope creep | Stick to coverage gap table; no new endpoints; ratchet floor only after tests merge |
| Duplicate `PostgresTestBase` under `persistence/` and `support/` | Consolidate in Q6a or Q8 as a prerequisite cleanup (single base class) |
| PIT slows CI | Report-only on PR; optional nightly job |
| Q12 without PRD | Mark DROPPED or BACKLOG until PRD lists baseline persistence |

### Verification cadence (every PR)

```bash
./gradlew check
./scripts/check-guardrails.sh
./scripts/audit-strength.sh
# After Q5 lands:
./scripts/ci-e2e-demo.sh
# In CI (must pass before merge):
CI=true ./gradlew clean check   # skipped=0 after Q8
```

### Immediate next action

**Activate Q5.** Create `scripts/ci-e2e-demo.sh` from `docs/demo-script.md`, add CI job to `.github/workflows/backline-ci.yml`, verify headless on Ubuntu with service container Postgres (or compose). Do not start Q6 until M1 (Q5 + Q8) exit criteria are met.

## Quality hardening phase (9/10 roadmap)

Only one step may be active at a time. Each step must leave a verification artifact.

### Target quality bar (9/10)

| Dimension | Current (~8.3) | Target (9.0) |
|-----------|----------------|--------------|
| API line coverage | 25.9% | >= 65% |
| CI E2E proof | none | demo path green in CI |
| CI skipped tests | 0 in CI (good) | remain 0; local skips documented |
| Mutation/property tests | none | executor + diff + policy covered |
| Operability | runbook exists | doctor + policy profiles actionable |
| Enforced gates | check + guardrails | check + guardrails + E2E + ratcheting coverage |

### Phase map (dependency order)

```txt
Q5  E2E demo in CI ─────────────────────────────┐
Q6  API coverage to 65% (layered test pyramid)  ├──> Q10 Coverage ratchet + quality dashboard
Q7  Policy profiles + doctor hardening         │
Q8  Eliminate silent test skips in CI           │
Q9  Property + mutation tests on critical logic │
Q11 Security/redaction test expansion           │
Q12 Operability: validate command + baseline UX (PRD-gated)
Q13 Performance smoke in CI (existing harness)  └──> Definition of done: overall 9/10 audit
```

### Step tracker

| Step | Status | Depends on | Exit criteria |
|------|--------|------------|---------------|
| Q1 Contracts + runbook + guardrail script | DONE | — | `docs/contracts.md`, `docs/runbook.md`, `./scripts/check-guardrails.sh` |
| Q2 API/service test expansion (initial) | DONE | — | `./gradlew :apps:api:test` green; API line 25.9% |
| Q3 Architecture enforcement | DONE | — | ArchUnit tests pass |
| Q4 CI coverage gates | DONE | — | `./gradlew check` + CI green |
| **Q5 E2E demo in CI** | BACKLOG | Q4 | See Q5 detail below |
| **Q6 API coverage to 65%** | BACKLOG | Q4 | See Q6 detail below |
| **Q7 Policy profiles + doctor hardening** | BACKLOG | Q4 | See Q7 detail below |
| **Q8 Zero skipped tests in CI** | BACKLOG | Q5 | CI `./gradlew check` reports `skipped=0` |
| **Q9 Property + mutation tests** | BACKLOG | Q6 | See Q9 detail below |
| **Q10 Coverage ratchet + dashboard** | BACKLOG | Q6,Q9 | Floors raised; README badge or CI summary |
| **Q11 Security/redaction tests** | BACKLOG | Q4 | Guardrail script + redaction tests pass |
| **Q12 Baseline UX commands** | BACKLOG | PRD update | `backline baseline set/show` + API persistence |
| **Q13 Performance smoke in CI** | BACKLOG | Q5 | Existing perf harness runs on PR with thresholds |

Verification commands (all phases):

```bash
./gradlew check
./scripts/check-guardrails.sh
./scripts/audit-strength.sh
```

---

### Q5 — E2E demo in CI

**Objective:** Prove Postgres → API → worker → sample API → CLI path unattended in GitHub Actions.

**Artifacts:**
- `scripts/ci-e2e-demo.sh` (headless runner derived from `docs/demo-script.md`)
- `.github/workflows/backline-ci.yml` — add `e2e-demo` job (or extend `verify`)
- `apps/cli/src/test/java/.../EndToEndDemoTest.java` (optional JUnit wrapper calling script assertions)

**Implementation outline:**
1. CI job services: PostgreSQL 16 container (or Testcontainers from script).
2. Start API + worker + sample-api as background processes (tmux or `&` with health wait loops).
3. `installDist` CLI; run: `doctor`, `sample init`, `run`, `history`, `diff`, `report`.
4. Assert exit codes, run ID printed, report file exists, run reaches terminal status.
5. Upload report.md + JSON as CI artifacts.

**Verification:**
```bash
./scripts/ci-e2e-demo.sh
# CI: e2e-demo job green on PR
```

**Must not:** Bypass CLI→API boundary; CLI must not write to Postgres directly.

---

### Q6 — API coverage to 65%

**Objective:** Raise `apps/api` line coverage from 25.9% to >= 65% with a test pyramid, not controller-only sprawl.

**Coverage gap analysis (priority order):**

| Layer | Target classes | Test type |
|-------|----------------|-----------|
| Services | `RunService`, `DiffService`, `CheckSyncService`, `CheckHistoryService`, `ProjectSummaryService`, `ProjectService` | `@SpringBootTest` + PostgresTestBase |
| Web | `ApiExceptionHandler`, all controllers | `@WebMvcTest` or TestRestTemplate integration |
| Persistence | custom repository queries, `RunSpecifications`, `OffsetBasedPageRequest` | repository integration tests |
| Mappers | `*Mapper`, `AssertionJsonMapper` | unit tests |
| Config | `JacksonConfig`, `OpenApiConfig` | smoke/unit |

**Sub-steps (execute in order):**
1. Q6a — Service tests for remaining untested paths (cancel, events, error branches).
2. Q6b — Controller contract matrix: every endpoint × success/validation/not-found.
3. Q6c — Repository query tests (`findPreviousPassedRun`, history projection, filters).
4. Q6d — Mapper/unit tests for JSON serialization edges (assertion operators).
5. Q6e — Raise JaCoCo floor from 0.28 → 0.40 → 0.55 → 0.65 (ratchet per sub-step merge).

**Verification:**
```bash
./gradlew :apps:api:test jacocoTestCoverageVerification
./scripts/audit-strength.sh  # apps/api line >= 65%
```

---

### Q7 — Policy profiles + doctor hardening

**Objective:** Make CI gating and local troubleshooting first-class without expanding into a general CI platform.

**PRD note:** Policy profiles and named presets require a small PRD addition (allowed: run policy thresholds already exist).

**Artifacts:**
- `RunPolicyProfile` enum or named presets in `backline.yml` (`strict`, `warn-only`)
- CLI: `backline run --policy strict` (maps to existing evaluator)
- Enhanced `backline doctor`: checks API, worker JAR, Postgres, config validity, sample API optional; non-zero exit on critical failure
- CLI smoke tests for each doctor failure mode
- Update `docs/ci-integration.md` and `docs/runbook.md`

**Verification:**
```bash
./gradlew :apps:cli:test
backline doctor  # actionable output, exit 1 when API down
backline run --policy strict --enforce-policy  # exit code documented
```

---

### Q8 — Zero skipped tests in CI

**Objective:** CI must never silently skip integration tests; local dev may skip when Docker absent.

**Artifacts:**
- Audit all `Assumptions.assumeTrue` / `@EnabledIf` usage in test bases
- Ensure `CI=true` throws (already done for worker/api bases — verify all modules)
- CI workflow sets `CI=true` explicitly
- `scripts/audit-strength.sh` fails if `skipped > 0` when `CI=true`

**Verification:**
```bash
CI=true ./gradlew check
# audit output: skipped=0
```

---

### Q9 — Property + mutation tests

**Objective:** Prove critical logic is not just covered but meaningfully tested.

**Scope (in PRD):**
- `libs/executor` — assertion evaluation against random JSON
- `libs/config` — config validation rejects malformed inputs
- `RunPolicyEvaluator` — threshold boundary tests
- `DiffService` — baseline selection edge cases

**Artifacts:**
- jqwik property tests in `libs/executor`, `libs/config`
- Optional: PIT mutation testing task for `libs/executor` and `DiffService` (Gradle plugin, CI nightly or PR label)
- Document acceptable mutation survivors in test class comments

**Verification:**
```bash
./gradlew :libs:executor:test :libs:config:test :apps:cli:test
# optional: ./gradlew pitest (threshold configured)
```

**Must not:** Add mutation testing as a merge blocker until baseline established (use report-only first).

---

### Q10 — Coverage ratchet + quality dashboard

**Objective:** Prevent coverage regression; make quality visible to reviewers.

**Artifacts:**
- Raise `coverageMinimums` in `build.gradle` after each phase
- CI step publishes coverage summary comment or artifact table
- README quality snapshot section auto-updated by `audit-strength.sh`
- Optional: Shields.io badge if repo is public

**Verification:**
```bash
./gradlew check  # fails if coverage drops below floor
```

---

### Q11 — Security/redaction test expansion

**Objective:** Automate guardrails that are today partially manual.

**Artifacts:**
- Unit tests: sensitive headers redacted in executor logs/previews
- URL validation tests: reject dangerous schemes if documented
- Response preview bound tests (extend existing)
- Expand `scripts/check-guardrails.sh`:
  - verify preview constant == docs
  - grep for `Runtime.exec` / `ProcessBuilder` in config
  - verify no `@SpringBootApplication` in CLI main
- Optional: OWASP dependency check Gradle task (report-only on PR)

**Verification:**
```bash
./scripts/check-guardrails.sh
./gradlew :libs:executor:test
```

---

### Q12 — Baseline UX commands (PRD-gated)

**Objective:** Improve diff ergonomics for daily use.

**Requires PRD.md update before coding** (new persisted baseline preference).

**Artifacts:**
- API: store baseline run preference per project/environment
- CLI: `backline baseline set <runId>`, `backline baseline show`
- Diff defaults to stored baseline when no flag provided
- Tests: API + CLI + diff integration

**Verification:**
```bash
./gradlew :apps:api:test :apps:cli:test
backline baseline set <id> && backline diff <newRunId>  # uses stored baseline
```

---

### Q13 — Performance smoke in CI

**Objective:** Catch gross regressions in worker throughput / queue latency using existing harness.

**Artifacts:**
- Wire `perf/scripts/` smoke profile into CI (nightly or post-E2E job)
- Document thresholds in `docs/runbook.md`
- Fail CI only on smoke profile breach (not full load test)

**Verification:**
```bash
# local
./perf/scripts/queue-load.ps1 -Profile smoke  # or documented equivalent
# CI: perf-smoke job green
```

---

### Explicitly out of scope (remain 9/10 without these)

Per `GUARDRAILS.md` — do not implement unless PRD is updated:

- Authentication / authorization
- Multi-tenant isolation
- Cloud sync / SaaS hosting
- Load testing as product feature (perf smoke is CI-only)
- HTML report generation
- OpenAPI-driven contract test generation

These are not required for 9/10 **local-first developer tool** quality; document as known limitations.

---

### Definition of done (overall 9/10)

The quality phase is complete when all are true:

1. `./gradlew check` passes locally and in CI with `skipped=0`
2. `./scripts/ci-e2e-demo.sh` passes in CI
3. API line coverage >= 65%
4. Property tests cover executor assertion evaluation
5. Doctor and policy profiles are documented and smoke-tested
6. Guardrail script covers security-sensitive paths
7. `PLAN.md` Q5–Q11 marked DONE; Q12/Q13 DONE or explicitly DROPPED with reason
8. Re-audit score >= 9.0 on all dimensions in project README or `docs/audit-playbook.md`

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
