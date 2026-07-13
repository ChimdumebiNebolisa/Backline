# Backline PRD

## Status

This document defines the complete build scope for Backline. This is one build, not a staged release plan. If a feature is not listed in this PRD, do not build it unless this document is updated first.

## Product summary

Backline is a local-first API regression ledger for backend developers.

It provides a terminal workflow for defining HTTP checks, submitting runs, executing those runs through a worker, storing results in PostgreSQL, comparing API behavior over time, and generating evidence reports.

Backline is not trying to replace Postman, Bruno, Hurl, Newman, k6, or full API monitoring SaaS products. The value is persistent, queryable regression history for API behavior.

## What the app is

Backline is:

- A CLI-driven developer tool.
- A Spring Boot backend API for storing projects, checks, runs, results, and reports.
- A PostgreSQL-backed regression history system.
- A worker-based API check executor.
- A local sample project generator so reviewers can test the system quickly.
- A portfolio-grade backend project that demonstrates API design, database design, migrations, transactions, validation, logging, health checks, tests, Docker setup, and documentation.

## What the app is not

Backline is not:

- A Postman replacement.
- A full GUI API client.
- A load testing tool.
- A SaaS monitoring platform.
- A multi-tenant enterprise product.
- An AI product.
- A frontend-heavy dashboard.
- A Kubernetes or Kafka project.
- A general CI platform.

## Primary actor

A backend developer who wants to know whether their API behavior changed across runs.

## Secondary actor

A reviewer, recruiter, or engineering interviewer who wants to run the project quickly and see concrete backend engineering proof.

## Main job to be done

When a developer changes an API, they need a fast way to run repeatable checks, store the results, compare those results against previous runs, and produce evidence of what changed.

## Core positioning

Backline is a terminal-first API regression ledger that stores every API check run in PostgreSQL and exposes a REST API, CLI, worker, and reports for inspecting how API behavior changes over time.

## Build scope

Everything in this section is part of the same build scope.

### Must-have capabilities

#### CLI

The CLI must support:

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

Rules:

- `backline run` submits a run to the API and waits for completion by default.
- `backline run --no-wait` submits a run and returns the run ID immediately.
- The CLI must not write directly to the database.
- The CLI must not duplicate check execution logic.
- The worker owns check execution.
- The API owns persistence, history, filtering, and comparison data.

#### Config file

Backline must use a YAML config file named `backline.yml`.

The config must support:

```yaml
project: sample-api
environment: local

checks:
  - key: health
    name: Health check
    method: GET
    url: http://localhost:8081/health
    expected_status: 200
    max_latency_ms: 300

  - key: get-user
    name: Fetch user
    method: GET
    url: http://localhost:8081/users/1
    expected_status: 200
    max_latency_ms: 500
    assertions:
      - path: $.id
        equals: 1
      - path: $.email
        exists: true

  - key: broken-endpoint
    name: Broken endpoint
    method: GET
    url: http://localhost:8081/broken
    expected_status: 200
    max_latency_ms: 500

  - key: schema-change
    name: Schema change endpoint
    method: GET
    url: http://localhost:8081/schema-change
    expected_status: 200
    contract:
      enabled: true
      severity: warn
      ignore_paths:
        - $.meta.generated_at
```

Validation rules:

- `project` is required.
- `environment` is required.
- Each check must have a stable `key`.
- Each check key must be unique within the project config.
- `method` must be a supported HTTP method.
- `url` must be absolute.
- `expected_status` must be between 100 and 599.
- `max_latency_ms`, when present, must be greater than zero.
- Assertions must use supported assertion types only.
- Optional per-check `contract` section may set `enabled`, `severity` (`warn` default), and bounded `ignore_paths` using a small path syntax (`$.a.b` and `[]` segments only).

#### Observed JSON response contracts

Backline must capture a versioned, canonical structural snapshot of JSON HTTP response bodies for each check when contract capture is enabled (warn-by-default when omitted).

Requirements:

- Record paths and JSON types only; never scalar values.
- Represent arrays with `[]` segments (no indexes).
- Persist contract JSON, SHA-256 fingerprint, and capture status on check results.
- Surface structural drift in run diffs, CLI output, and reports without claiming OpenAPI validation.
- Bound capture by response bytes, depth, path count, and serialized size; truncated captures remain visible.
- Contract drift must not by itself change check HTTP status unless an explicit policy later treats it as failure.

This is observed-response drift detection. A single observed response cannot prove that a field is formally required or optional.

#### Backend API

The API must support:

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

API requirements:

- Consistent request and response shapes.
- Input validation.
- Structured errors.
- Pagination for list endpoints.
- Filtering by project, environment, status, date range, and check key where relevant.
- OpenAPI documentation.
- Health endpoints.
- Database readiness surfaced through Actuator health checks.

#### Worker

The worker must:

- Poll queued runs.
- Claim jobs safely using database locking.
- Execute HTTP checks.
- Record result status, actual status code, latency, error details, response preview, assertion results, and observed JSON response-contract snapshots when capture is enabled.
- Retry failed worker attempts only for worker errors, not failed API assertions.
- Enforce valid status transitions.
- Use transactions when claiming and finalizing runs.
- Support multiple workers without double-processing the same run.

Required run statuses:

```txt
QUEUED
RUNNING
PASSED
FAILED
ERROR
CANCELLED
```

#### Database

The database must use PostgreSQL and Flyway migrations.

Required entities:

- `projects`
- `checks`
- `runs`
- `check_results`
- `run_events`

Minimum data requirements:

- Stable project slug.
- Stable check key.
- Immutable run record.
- Result row per check execution.
- Run status history or events.
- Config hash for run reproducibility.
- Timestamps for queue, start, finish, and result creation.

Required database behavior:

- Foreign keys.
- Unique constraints.
- Status check constraints.
- Indexes for history and filtering.
- Transactions for run creation, worker claim, result writing, and run finalization.
- Duplicate prevention through idempotency key or unique run request identity.

#### Regression comparison

Backline must compare a run against the previous completed run for the same project and environment.

The diff must show:

- Newly failing checks.
- Newly passing checks.
- Checks still failing.
- Checks still passing.
- Status code changes.
- Latency increase or decrease.
- Assertion changes.
- Observed JSON response-contract structural drift (breaking, additive, noisy, unavailable, truncated).
- Missing or newly added checks.

Diff precedence for the primary change type must prefer status transitions and HTTP status-code changes over breaking contract drift, then assertion changes, then additive or noisy contract drift, then latency, then still-passing or still-failing. Contract change details remain attached even when another primary type wins.

#### Reports

Backline must generate a report from stored API data.

Supported report command:

```bash
backline report <runId>
```

Minimum report content:

- Project name.
- Environment.
- Run ID.
- Run status.
- Started and finished timestamps.
- Check summary.
- Failed checks.
- Latency summary.
- Diff against previous completed run.
- Known limitations section.
- Generated timestamp.

Markdown report is required. HTML report is allowed but not required.

#### Sample input and demo path

Backline must include a fast sample path.

Required commands:

```bash
backline sample init
backline sample serve
backline run
backline history
backline diff <runId>
backline report <runId>
```

`backline sample init` must create:

```txt
examples/sample-api/backline.yml
examples/sample-api/README.md
```

`backline sample serve` must start a local sample API with predictable endpoints:

```txt
GET  /health
GET  /users/1
POST /users
GET  /slow
GET  /broken
GET  /schema-change
```

The sample must include at least one passing check and one intentionally failing check.

#### Production readiness

The project must include:

- `.env.example`
- Docker Compose for API, worker, and PostgreSQL
- Structured logging
- Spring Boot Actuator health checks
- OpenAPI docs
- Unit tests
- Integration tests
- CLI smoke test
- Worker execution test
- Database migration verification
- README with run, test, and demo instructions
- Known limitations document or section

## Should-have capabilities

These are included only if the must-have scope remains clean and verified:

- HTML report output.
- Environment variable substitution in `backline.yml`.
- JSONPath assertion support beyond basic `exists` and `equals`.
- GitHub Actions example.
- One architecture diagram in Markdown using Mermaid.

## Nice-to-have capabilities

These must not block the build:

- Export results as JSON.
- Export results as JUnit XML.
- Small read-only browser page for run history.
- CLI shell completion.

## Out of scope

Do not build:

- Authentication.
- User accounts.
- Cloud sync.
- SaaS hosting workflow.
- Payment or subscriptions.
- Load testing.
- Fuzz testing.
- AI analysis.
- Full OpenAPI contract testing.
- Inferring formal required versus optional fields from a single observed response.
- Storing complete response bodies as part of contract capture.
- Team permissions.
- Frontend dashboard.
- Kafka, Kubernetes, or service discovery.
- Plugin system.
- Browser extension.

## Acceptance criteria

The build is accepted only when all criteria below are true:

- `docker compose up` starts PostgreSQL, the API, and a worker.
- `GET /actuator/health` returns an application health response.
- `backline sample init` creates the sample config and README.
- `backline sample serve` starts the local sample API.
- `backline run` submits a run and waits for completion.
- The worker executes all sample checks.
- At least one sample check passes and one sample check fails.
- `backline history` shows stored runs from PostgreSQL.
- `backline diff <runId>` compares the selected run to the previous completed run.
- `backline report <runId>` creates a Markdown report.
- API list endpoints support pagination.
- Filtering works for run status and project.
- Invalid input returns structured validation errors.
- Migrations create the schema from an empty database.
- Tests verify core service, API, database, worker, and CLI flows.
- README explains setup, run commands, testing, architecture, limitations, and demo path.

## Constraints

- Java 21.
- Spring Boot for API and worker runtime.
- Picocli for CLI commands.
- PostgreSQL for persistence.
- Flyway for migrations.
- Testcontainers for integration tests.
- Docker Compose for local runtime.
- No direct database writes from the CLI.
- No duplicated business logic between CLI and worker.
- No placeholder logic in the core run path.
- No silent failures.
- No staged release labels in project docs.

## Blocked unknowns

These must be resolved before coding if they affect implementation:

- Final repository name.
- Maven vs Gradle decision.
- Exact Java package name.
- Whether CLI, API, worker, and sample API are packaged as separate executables or one multi-command distribution.
- Whether Markdown-only reports are enough or HTML is required.
