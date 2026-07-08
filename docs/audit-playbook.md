# Backline Audit Playbook

This playbook strengthens existing features by running repeatable quality checks instead of adding scope.

## One-command audit

```bash
./scripts/audit-strength.sh
```

This command runs tests, emits coverage summaries, runs sample API runtime smoke, and reports whether API runtime checks are blocked by missing PostgreSQL.

## 1) Fast correctness baseline

```bash
./gradlew test
```

Expected:

- all tests pass
- no failing task in any module

## 2) Coverage artifact generation

Backline now emits JaCoCo XML + HTML reports for each Java module.

```bash
./gradlew test jacocoTestReport
```

Coverage report locations:

- `apps/*/build/reports/jacoco/test/html/index.html`
- `libs/*/build/reports/jacoco/test/html/index.html`
- XML under corresponding `.../jacoco/test/jacocoTestReport.xml`

Use this to identify weakly-tested existing behavior (controller edge cases, retry/cancellation paths, CLI error handling).

## 3) Runtime boot checks

### Sample API (no database dependency)

```bash
./gradlew :apps:sample-api:bootRun
curl http://localhost:8081/health
```

Expected:

- process starts cleanly
- `/health` returns `{"status":"UP"...}`

### API server + worker (requires PostgreSQL on localhost:5432)

```bash
./gradlew :apps:api:bootRun
./gradlew :apps:worker:bootRun --args="--spring.main.keep-alive=true"
```

Expected:

- API starts and Flyway completes
- worker starts and remains alive

If API fails with `Connection to localhost:5432 refused`, run the database first (Docker or local PostgreSQL).

## 4) Contract smoke checks

With API running:

```bash
curl -sS http://localhost:8080/actuator/health
curl -sS http://localhost:8080/v3/api-docs
```

Expected:

- health is `UP`
- OpenAPI document loads

## 5) Regression-focused assertions

Audit that assertion operators remain aligned across config/API/executor:

- `equals`, `exists`, `not_equals`, `contains`, `regex`, `gt`, `gte`, `lt`, `lte`

Use failing and passing samples to verify:

- config parsing behavior
- `/api/checks/sync` validation behavior
- worker execution behavior

## 6) Data integrity checks

Focus checks:

- no duplicate `check_results` per `(run_id, check_key)`
- run transitions remain valid
- retry/requeue path keeps DB state consistent
- stale recovery behavior does not lose terminal events

Primary verification comes from:

```bash
./gradlew :apps:worker:test :apps:api:test
```

## 7) Redundancy and drift checks

Before release:

- compare API behavior vs `docs/api-examples.md`
- compare architecture rules vs `ARCHITECTURE.md`
- ensure `README.md` command examples still match implemented flags/options

## Suggested audit cadence

- Every PR: steps 1, 2, and module-focused tests.
- Before merge to main: steps 1–4.
- Weekly hardening pass: steps 1–7 with a quick findings summary.
