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
ACTIVE: none
BLOCKED: none
DONE: Tasks 1-6, integration fixes A-E, Testcontainers verification, worker hardening, perf harness
DROPPED: none
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
