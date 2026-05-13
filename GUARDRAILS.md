# Backline Guardrails

## Purpose

This document defines enforceable rules for building Backline. If a rule cannot be checked, rewrite the rule until it can be checked.

## Scope guardrails

- Do not add features that are not listed in `PRD.md`.
- Do not use staged release labels such as `v1`, `v2`, or `phase`.
- Do not add authentication, cloud sync, frontend dashboards, load testing, AI analysis, Kafka, Kubernetes, or plugins.
- Do not turn Backline into a Postman replacement.
- Do not add a feature because it is interesting. Add it only if it supports the PRD.
- If scope needs to change, update `PRD.md` before coding.

## Architecture guardrails

- Do not change ownership boundaries without updating `ARCHITECTURE.md`.
- CLI must communicate with the API over HTTP.
- CLI must not write directly to PostgreSQL.
- CLI must not execute production checks.
- Worker owns check execution.
- API owns persistence, history, filtering, pagination, diff data, and structured errors.
- PostgreSQL is the only durable state store.
- Report generation must use API data, not direct database access.
- Sample API must stay isolated from production API logic.

## Code quality guardrails

- Write the minimum code needed to satisfy the current documented requirement.
- Do not add speculative abstractions.
- Do not refactor unrelated code.
- Do not duplicate business logic between CLI, API, and worker.
- Do not hide business rules inside controllers or command classes.
- Keep validation deterministic.
- Keep error handling explicit.
- Prefer small services with clear responsibilities.
- Do not create utility dumping grounds.
- Do not leave dead code.
- Do not leave placeholder logic in core flows.
- Do not leave TODOs in must-have paths unless the blocker is documented in `PLAN.md`.

## API guardrails

- Every endpoint must have a documented request and response shape.
- Every endpoint must return structured errors.
- List endpoints must support pagination.
- Filtering must be validated.
- Unknown filters must fail clearly.
- Invalid IDs must return a clear not-found or validation error.
- API responses must not expose stack traces.
- Controllers should delegate business logic to services.
- OpenAPI documentation must reflect implemented endpoints.

## Database guardrails

- Every schema change must be done through Flyway migration.
- No manual schema changes outside migrations.
- Foreign keys are required for relational ownership.
- Status fields must use constraints or validated enums.
- Duplicate prevention must be enforced at the database level where possible.
- Indexes must exist for common query patterns.
- Run creation, worker claim, result writing, and finalization must be transactional.
- A run must not be processed by two workers.
- A run must not be finalized without result rows unless it ends in `ERROR`.
- Result rows must be immutable after finalization unless a repair migration or documented admin action is added.

## Worker guardrails

- Worker claim must use a concurrency-safe database transaction.
- Worker must write run events for major status transitions.
- Worker retries are only for worker/runtime errors.
- Failed API assertions are test failures, not retryable infrastructure errors.
- Worker must not silently skip checks.
- Worker must mark unrecoverable execution failures as `ERROR`.
- Worker must finalize every claimed run unless the process crashes.
- Stale `RUNNING` recovery must be explicit if implemented.

## CLI guardrails

- CLI output must be readable without requiring a web UI.
- CLI commands must fail with actionable messages.
- `backline doctor` must check API connectivity, config readability, and expected environment variables.
- `backline run` must print the run ID.
- `backline run` must make it clear whether it is waiting or returning immediately.
- `backline sample init` must not overwrite existing files without explicit confirmation or a force flag.
- `backline report` must print the generated file path.

## Sample data guardrails

- Sample config must run against the included sample API.
- Sample must include at least one passing check.
- Sample must include at least one intentionally failing check.
- Sample must include at least one latency-sensitive check.
- Sample must not require internet access.
- Sample setup must be documented in the README.

## Security and safety guardrails

- Do not log secrets.
- Redact configured sensitive headers such as `Authorization`, `Cookie`, and `Set-Cookie`.
- Do not store full response bodies by default.
- Store only a bounded response preview.
- Limit maximum response preview size.
- Set HTTP client timeouts.
- Validate URLs before execution.
- Do not follow redirects unless explicitly configured.
- Do not allow file system writes outside expected report and example directories.
- Do not execute shell commands from config files.

## Testing guardrails

Required tests:

- Config parser unit tests.
- Config validation unit tests.
- API validation tests.
- Repository or database integration tests with Testcontainers.
- Migration smoke test from empty database.
- Worker claim concurrency test.
- Worker run execution test.
- CLI command smoke tests.
- Report generation test.
- Sample config end-to-end test.

Testing rules:

- A feature is not complete without tests or a documented reason why it cannot be tested.
- Use the narrowest test that proves the behavior.
- Integration tests must use real PostgreSQL through Testcontainers.
- Do not mock database behavior for transaction-critical logic.
- Do not claim Docker setup works without running it.

## Verification guardrails

Every completed step in `PLAN.md` must include at least one verification artifact.

Accepted verification artifacts:

- Unit test output.
- Integration test output.
- CLI output.
- API response output.
- Docker Compose output.
- Actuator health response.
- Generated report file.
- Migration output.
- Screenshot only when a visual proof is useful.
- Log excerpt only when proving worker or runtime behavior.

Claims without verification are unverified.

## Documentation guardrails

- README must explain how to run, test, and demo the project.
- README must include the sample path.
- README must include known limitations.
- README must explain why Backline is not a Postman replacement.
- API examples must be copyable.
- Architecture changes must be reflected in `ARCHITECTURE.md`.
- Scope changes must be reflected in `PRD.md`.
- Execution status changes must be reflected in `PLAN.md`.

## Definition of done

The build is done only when:

- All must-have PRD items are complete.
- Core flows have verification artifacts.
- Active blockers are resolved or explicitly accepted.
- No unresolved guardrail violations remain.
- The delivered system still matches `PRD.md` and `ARCHITECTURE.md`.
- README provides a reviewer-friendly demo path.
