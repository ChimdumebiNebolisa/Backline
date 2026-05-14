# AGENTS.md

## Project

Backline is a local-first API regression ledger for backend developers.

The project contains:

- Picocli CLI.
- Spring Boot API server.
- Spring Boot worker.
- PostgreSQL database.
- Flyway migrations.
- Sample API.
- Markdown report generator.

Follow `PRD.md`, `ARCHITECTURE.md`, `GUARDRAILS.md`, and `PLAN.md` before coding.

## Required skill

Use this clean-code working style for the whole project.

If using an AI coding environment that supports skills, install the security skill before implementation work:

```bash
npx skills add https://github.com/raroque/vibe-security-skill --skill vibe-security
```

## Working agreements

- Think before coding.
- State assumptions explicitly.
- If something is unclear, say what is unclear instead of guessing.
- If multiple valid interpretations exist, present them instead of choosing silently.
- Prefer the simplest approach that fully solves the task.
- Push back on unnecessary complexity.
- Treat the PRD as the source of scope.
- Treat Architecture as the source of ownership boundaries.
- Treat Guardrails as the source of forbidden behavior.
- Treat Plan as the execution tracker.

## Implementation rules

- Write the minimum code necessary.
- Do not add features beyond what was asked.
- Do not add abstractions, configurability, or flexibility unless requested by the docs.
- Do not refactor unrelated code.
- Do not clean up adjacent code, comments, or formatting unless the task requires it.
- Match the existing style and local conventions.
- Remove only the unused imports, variables, or functions that your own changes made obsolete.
- If you notice unrelated dead code or design issues, mention them instead of changing them.
- Do not create broad utility classes for unrelated behavior.
- Do not hide business rules in controllers or CLI command handlers.
- Do not duplicate logic between CLI, API, and worker.
- Prefer explicit names over clever names.
- Prefer small, testable services over large orchestration classes.
- Keep error handling explicit.
- Keep validation deterministic.

## Documentation rules for clean code

Code documentation must explain intent, constraints, and non-obvious tradeoffs.

Do:

- Document public commands.
- Document public API DTOs when field meaning is not obvious.
- Document transaction-sensitive service methods.
- Document worker claim and retry behavior.
- Document config parsing rules.
- Document security-sensitive behavior such as redaction and response preview limits.
- Document why a non-obvious decision was made.

Do not:

- Comment obvious code.
- Repeat method names in comments.
- Leave stale comments.
- Use comments to excuse confusing code.
- Hide TODOs in core flows.
- Add large block comments when a smaller method name would be clearer.

Good comment example:

```java
// The worker claims runs with SKIP LOCKED so multiple workers can poll
// without processing the same queued run.
```

Bad comment example:

```java
// Loop through results.
```

## Execution rules

- Turn the request into a concrete goal before coding.
- For non-trivial tasks, write a brief plan with verification for each major step.
- Prefer verifiable progress over broad rewrites.
- Only one plan step may be active at a time.
- Do not move to the next step until the current step is verified.
- If verification fails, the step is not done.
- If a step exposes a scope issue, stop and update `PRD.md`.
- If a step exposes an ownership issue, stop and update `ARCHITECTURE.md`.
- If a step exposes an enforcement issue, stop and update `GUARDRAILS.md`.

Example format:

```txt
1. Create migration for runs table -> verify: migration test with PostgreSQL Testcontainers
2. Add run submission endpoint -> verify: API integration test
3. Add CLI command -> verify: CLI smoke test
```

## Verification rules

- Never claim success without verification.
- Use the narrowest reasonable check.
- For bug fixes, reproduce the issue before confirming the fix when possible.
- For refactors, confirm behavior is unchanged.
- If something could not be verified, say that explicitly.
- Every completed step must leave behind one of:
  - working behavior
  - passing check
  - created or updated artifact
  - resolved blocker

Accepted verification artifacts:

- Unit test output.
- Integration test output.
- Testcontainers output.
- CLI output.
- API response output.
- Docker Compose output.
- Actuator health response.
- Generated report file.
- Migration output.
- Relevant log excerpt.

## Communication rules

- Separate facts, assumptions, and interpretation.
- Surface tradeoffs early.
- Do not hide uncertainty.
- Report what changed, how it was verified, and what remains uncertain.
- Do not claim that the full system works after only a narrow check.
- Be specific about commands run and results observed.

## Backend rules

- Controllers must stay thin.
- Services own business logic.
- Repositories own database access.
- DTOs must not leak persistence internals unnecessarily.
- Use transactions around multi-row state changes.
- Validate input at API boundaries.
- Enforce critical integrity rules in PostgreSQL as well as application code.
- Use structured errors.
- Do not expose stack traces through API responses.
- Add indexes for documented query patterns.
- Avoid N+1 query patterns in history and diff endpoints.

## CLI rules

- CLI commands must have useful help text.
- CLI errors must be actionable.
- CLI must call the API for persistence.
- CLI must not write directly to PostgreSQL.
- CLI must not execute production checks.
- `backline run` must print the run ID.
- `backline report` must print the generated path.
- `backline doctor` must check config, API connectivity, and required environment settings.

## Worker rules

- Worker must claim queued runs with a concurrency-safe transaction.
- Worker must not process the same run twice.
- Worker must write run events for major transitions.
- Worker must distinguish API assertion failure from worker/runtime error.
- Worker must use bounded HTTP timeouts.
- Worker must redact sensitive headers from logs.
- Worker must store bounded response previews only.

## Testing rules

- Unit test deterministic logic.
- Integration test database behavior with PostgreSQL Testcontainers.
- Do not mock transaction-critical database behavior.
- Add tests for validation failures, not only success paths.
- Add at least one concurrency test for worker claim behavior.
- Add CLI smoke tests for command parsing and output.
- Add a sample end-to-end test if practical.

## Security rules

- Do not log secrets.
- Redact `Authorization`, `Cookie`, and `Set-Cookie`.
- Do not execute shell commands from config.
- Do not allow config-driven file writes.
- Do not store full response bodies by default.
- Keep response preview size bounded.
- Validate URLs before execution.
- Use safe defaults for redirect behavior and timeouts.

## Scope control

Before writing code, confirm:

```txt
Is this in PRD.md?
Does Architecture.md say where it belongs?
Do Guardrails.md allow it?
Is it the active step in PLAN.md?
How will it be verified?
```

If any answer is no, stop and update the relevant document before coding.

## Cursor Cloud specific instructions

### Services overview

| Service | Module | Port | Start command |
| --- | --- | --- | --- |
| PostgreSQL 16 | Docker container | 5432 | `docker start backline-postgres` |
| API server | `apps/api` | 8080 | `./gradlew :apps:api:bootRun` |
| Worker | `apps/worker` | — | `./gradlew :apps:worker:bootRun --args="--spring.main.keep-alive=true"` |
| Sample API | `apps/sample-api` | 8081 | `./gradlew :apps:sample-api:bootRun` |
| CLI | `apps/cli` | — | Build: `./gradlew :apps:cli:installDist`, then add to PATH: `export PATH="/workspace/apps/cli/build/install/backline/bin:$PATH"` |

### Non-obvious caveats

- **Worker requires `--spring.main.keep-alive=true`**: The worker poll thread is set as a daemon thread. Without this flag the JVM exits immediately after Spring context initialization. Always pass `--args="--spring.main.keep-alive=true"` when running via `bootRun`.
- **PostgreSQL must be running before API or Worker**: The API server runs Flyway migrations on startup and will fail if PostgreSQL is not available. The Worker also requires the database.
- **Testcontainers base classes use static initializer pattern**: Both `persistence.PostgresTestBase` and `PostgresWorkerTestBase` start the Testcontainers container via `static {}` (not `@Container`/`@Testcontainers`) to avoid Spring context caching conflicts. Do not switch to `@Container`.
- **CLI install directory**: The `installDist` task puts the distribution under `apps/cli/build/install/backline/bin` (not `cli/bin`).
- **Health checks**: API health at `http://localhost:8080/actuator/health`, Sample API health at `http://localhost:8081/health`.
- **Swagger UI**: Available at `http://localhost:8080/swagger-ui/index.html` when the API is running.

### Standard commands reference

Build, test, and demo steps are documented in `README.md`. API curl examples in `docs/api-examples.md`. Demo script in `docs/demo-script.md`.
