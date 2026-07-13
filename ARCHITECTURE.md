# Backline Architecture

## Purpose

This document defines Backline system boundaries, ownership, interfaces, data model, folder structure, and implementation constraints.

Do not change responsibilities, add new layers, or introduce new services without updating this document first.

## Architectural summary

Backline is a local-first backend system with five major parts:

1. CLI
2. API server
3. Worker
4. PostgreSQL database
5. Sample API and report generator

The CLI is the user interface. The API server owns persistence and queryable history. The worker owns check execution. PostgreSQL stores all durable state. The sample API exists only to make the project easy to test and demo.

## Major system parts

### CLI

Ownership:

- Reads command arguments.
- Reads `backline.yml`.
- Validates config shape before submission.
- Calls the API.
- Prints terminal output.
- Polls run status when `backline run` waits for completion.
- Generates local report files using API data.
- Starts sample API through `backline sample serve`.

The CLI must not:

- Write directly to PostgreSQL.
- Execute production checks directly.
- Own run status transitions.
- Duplicate API or worker business logic.

Primary commands:

```bash
backline init
backline sample init
backline sample serve
backline run
backline run --no-wait
backline status <runId>
backline history
backline diff <runId>
backline report <runId>
backline worker
backline doctor
```

### API server

Ownership:

- Accepts project, check, and run requests.
- Validates incoming API payloads.
- Persists durable state.
- Provides run history.
- Provides filtering and pagination.
- Provides run diff data.
- Exposes health checks.
- Exposes OpenAPI documentation.
- Returns structured errors.

The API server must not:

- Execute HTTP checks itself.
- Contain CLI rendering logic.
- Generate sample files.

### Worker

Ownership:

- Claims queued runs.
- Executes HTTP checks.
- Evaluates assertions.
- Writes check results.
- Writes run events.
- Finalizes run status.
- Handles retries for worker execution errors.

The worker must not:

- Accept direct user input.
- Own API response formatting.
- Change project or check definitions except through run result writes.
- Process the same run twice.

Worker claim model:

```sql
SELECT id
FROM runs
WHERE status = 'QUEUED'
  AND next_attempt_at <= now()
ORDER BY queued_at ASC
FOR UPDATE SKIP LOCKED
LIMIT 1;
```

The selected run is marked `RUNNING` in the same transaction.

### PostgreSQL database

Ownership:

- Durable state.
- Relational integrity.
- Queryable history.
- Run status constraints.
- Idempotency enforcement.
- Filtering and aggregation performance.

PostgreSQL is the only durable state store.

### Sample API

Ownership:

- Provides predictable endpoints for demos and tests.
- Produces known passing, failing, slow, and schema-change responses.
- Runs locally only.

The sample API must not be required in production mode.

### Report generator

Ownership:

- Uses API data to produce Markdown report files.
- Shows run summary, failures, latency, and diff.
- Does not query the database directly.

## Interfaces between parts

### CLI to API

Protocol: HTTP JSON.

Examples:

```txt
POST /api/checks/sync
POST /api/runs
GET  /api/runs/{runId}
GET  /api/runs/{runId}/results
GET  /api/runs/{runId}/diff
```

### Worker to database

Protocol: JDBC through Spring repositories or data access layer.

The worker claims jobs and writes results through transactional services.

### API to database

Protocol: JDBC through Spring repositories or data access layer.

The API reads and writes durable state through service methods.

### CLI to sample API

Protocol: local process launch or embedded Java HTTP server.

The sample API exposes endpoints consumed by Backline checks.

## Core entities

### projects

Purpose: group checks and runs.

Required fields:

```txt
id
slug
name
created_at
updated_at
```

Constraints:

```txt
slug unique
slug not null
```

### checks

Purpose: store current check definitions by stable key.

Required fields:

```txt
id
project_id
key
name
method
url
expected_status
max_latency_ms
assertions_json
contract_json
config_hash
active
created_at
updated_at
```

Constraints:

```txt
(project_id, key) unique
project_id foreign key
method allowed set
expected_status between 100 and 599
max_latency_ms greater than 0 when present
```

`contract_json` stores the per-check observed-contract capture settings (`enabled`, `severity`, `ignore_paths`) when present.

### runs

Purpose: represent one submitted regression run.

Required fields:

```txt
id
project_id
environment
status
idempotency_key
config_hash
source
queued_at
started_at
finished_at
locked_by
locked_at
attempt_count
next_attempt_at
created_at
updated_at
```

Constraints:

```txt
project_id foreign key
status allowed set
idempotency_key unique when present
attempt_count >= 0
```

Statuses:

```txt
QUEUED
RUNNING
PASSED
FAILED
ERROR
CANCELLED
```

### check_results

Purpose: store result of one check inside one run.

Required fields:

```txt
id
run_id
check_id
check_key
check_name
status
actual_status
latency_ms
error_code
error_message
response_preview
assertions_json
created_at
```

Constraints:

```txt
run_id foreign key
check_id foreign key nullable only if check was removed after run snapshot
(run_id, check_key) unique
status allowed set
latency_ms >= 0 when present
```

Result statuses:

```txt
PASSED
FAILED
ERROR
SKIPPED
```

### run_events

Purpose: create an auditable event trail for run state changes.

Required fields:

```txt
id
run_id
event_type
message
created_at
```

Constraints:

```txt
run_id foreign key
event_type not null
```

## API contract style

All successful responses use one of these shapes:

Single resource:

```json
{
  "data": {}
}
```

List response:

```json
{
  "data": [],
  "page": {
    "limit": 25,
    "offset": 0,
    "total": 100
  }
}
```

Error response:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "expected_status must be between 100 and 599",
    "field": "expected_status"
  }
}
```

## Required endpoints

```txt
GET    /actuator/health
GET    /api/health
POST   /api/projects
GET    /api/projects
GET    /api/projects/{projectId}
POST   /api/checks/sync
POST   /api/runs
GET    /api/runs
GET    /api/runs/{runId}
GET    /api/runs/{runId}/results
GET    /api/runs/{runId}/diff
GET    /api/projects/{projectId}/summary
GET    /api/checks/{checkId}/history
```

## Chosen stack

```txt
Language: Java 21
Backend runtime: Spring Boot
CLI: Picocli
Database: PostgreSQL
Migrations: Flyway
Testing: JUnit 5, Testcontainers
API docs: springdoc-openapi
Build tool: Gradle multi-project build
Local runtime: Docker Compose
Logging: SLF4J with structured JSON-friendly fields
```

## Repository structure

```txt
backline/
  AGENTS.md
  PRD.md
  ARCHITECTURE.md
  GUARDRAILS.md
  PLAN.md
  README.md
  docker-compose.yml
  .env.example

  apps/
    cli/
      src/main/java/...
      src/test/java/...

    api/
      src/main/java/...
      src/test/java/...

    worker/
      src/main/java/...
      src/test/java/...

    sample-api/
      src/main/java/...
      src/test/java/...

  libs/
    core/
      src/main/java/...
      src/test/java/...

    config/
      src/main/java/...
      src/test/java/...

    executor/
      src/main/java/...
      src/test/java/...

    reporting/
      src/main/java/...
      src/test/java/...

  db/
    migration/
      V1__create_projects.sql
      V2__create_checks.sql
      V3__create_runs.sql
      V4__create_check_results.sql
      V5__create_run_events.sql
      V6__add_indexes.sql

  examples/
    sample-api/
      backline.yml
      README.md

  docs/
    api-examples.md
    known-limitations.md
    demo-script.md
```

## Module responsibilities

### apps/cli

Contains Picocli commands and terminal rendering.

Depends on:

- `libs/config`
- `libs/core`
- `libs/reporting`

Calls API through HTTP client.

### apps/api

Contains Spring Boot API server.

Depends on:

- `libs/core`
- database migration setup
- repository/data access code

Exposes REST API, OpenAPI docs, and Actuator health checks.

### apps/worker

Contains Spring Boot worker runtime.

Depends on:

- `libs/core`
- `libs/executor`

Claims queued runs and executes checks.

### apps/sample-api

Contains local sample API for demo and tests.

Must stay small and predictable.

### libs/core

Contains shared domain models, enums, DTOs, error codes, and validation primitives that are not tied to HTTP or CLI rendering.

### libs/config

Contains YAML parsing and config validation.

### libs/executor

Contains HTTP execution, assertion evaluation, and bounded observed JSON response-contract capture.

Used by worker only.

### libs/reporting

Contains Markdown report generation from API response data.

## Transaction boundaries

### Run submission

Transaction:

1. Require existing project by slug.
2. Create run with `QUEUED` status.
3. Write run event.
4. Commit.

Client orchestration:

- CLI performs project creation and check sync in separate API calls before run submission.

### Worker claim

Transaction:

1. Select one queued run with `FOR UPDATE SKIP LOCKED`.
2. Mark run as `RUNNING`.
3. Set `locked_by`, `locked_at`, `started_at`.
4. Write run event.
5. Commit.

### Result writing and finalization

Transaction:

1. Insert all check results for the claimed run.
2. Compute final run status.
3. Mark run as `PASSED`, `FAILED`, or `ERROR`.
4. Set `finished_at`.
5. Write run event.
6. Commit atomically.

## Rejected alternatives

### Full microservices

Rejected because it adds complexity without improving the portfolio signal. The worker process is enough to show asynchronous processing, job ownership, concurrency safety, and retry logic.

### Kafka or Redis queue

Rejected for the build scope. PostgreSQL-backed jobs are enough and keep the system easier to run locally.

### Frontend dashboard

Rejected because the project is intended to show backend strength. Reports and CLI output provide enough proof.

### Direct CLI to database

Rejected because it weakens the API story and creates two persistence paths.

### CLI-owned check execution

Rejected because it duplicates worker logic and reduces the value of the async backend.
