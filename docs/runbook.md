# Backline operations runbook

## Startup order

1. PostgreSQL (`docker start backline-postgres` or `docker compose up -d postgres`)
2. API server (`./gradlew :apps:api:bootRun`)
3. Worker (`./gradlew :apps:worker:bootRun --args="--spring.main.keep-alive=true"`)
4. Optional sample API (`./gradlew :apps:sample-api:bootRun` or `backline sample serve`)

## Health checks

| Service | URL |
|---------|-----|
| API | `http://localhost:8080/actuator/health` |
| Sample API | `http://localhost:8081/health` |
| OpenAPI | `http://localhost:8080/swagger-ui/index.html` |

Worker has no HTTP port. Confirm it stays alive and logs `Worker started`.

## Common failures

### API fails on startup: `Connection to localhost:5432 refused`

PostgreSQL is not running. Start Postgres before API or worker.

### Worker exits immediately

Pass keep-alive:

```bash
./gradlew :apps:worker:bootRun --args="--spring.main.keep-alive=true"
```

### `backline doctor` reports API unreachable

Confirm API is running on port 8080 and `BACKLINE_API_URL` (if set) is correct.

### Run stuck in `RUNNING`

Possible worker crash. Stale recovery requeues or marks `ERROR` based on retry policy. Inspect `runs.last_error` and `run_events`.

### Testcontainers tests skipped locally

Docker is required. Start Docker Desktop and rerun `./gradlew test`.

In CI, skipped integration tests fail the build by design.

## Inspecting a run

```bash
curl -s http://localhost:8080/api/runs/<runId> | jq .
curl -s http://localhost:8080/api/runs/<runId>/results | jq .
```

## Quality checks before merge

```bash
./gradlew check
./scripts/audit-strength.sh
./scripts/check-guardrails.sh
```

See [audit-playbook.md](audit-playbook.md) for details.
