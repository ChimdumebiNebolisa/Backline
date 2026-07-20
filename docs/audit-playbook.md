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

After Q10, automate the README and api-examples checks:

```bash
./scripts/check-contract-drift.sh
```

## Suggested audit cadence

- Every PR: steps 1, 2, and module-focused tests.
- Before merge to main: steps 1–4.
- Weekly hardening pass: steps 1–7 with a quick findings summary.

## 8) Quality score rubric (9/10 sign-off)

Use this rubric for Q14 re-audit sign-off. Score each dimension **0–10**. **Overall = weighted average**. Target: **>= 9.0** on every dimension and overall.

| Dimension | Weight | 9.0 means | How measured |
|-----------|--------|-----------|--------------|
| Correctness | 25% | Zero test failures; E2E demo green in CI | `CI=true ./gradlew check`; `./scripts/ci-e2e-demo.sh` |
| Coverage depth | 20% | API line >= 65% **and** branch >= 40%; worker >= 55% line; CLI >= 60% line; core >= 50% line | JaCoCo XML via `./scripts/audit-strength.sh` |
| Full-stack proof | 15% | Unattended demo + extended smoke (policy, JSON report) green | CI `e2e-demo` job artifacts |
| Test rigor | 15% | jqwik on executor, config, policy, diff; no silent CI skips | Module test output; `CI=true` skip count = 0 |
| Security / guardrails | 10% | Redaction + preview tests; expanded `check-guardrails.sh` | `./scripts/check-guardrails.sh`; executor tests |
| Operability | 10% | Doctor checks documented failures; policy profiles smoke-tested | CLI smoke tests; `docs/runbook.md` |
| Sustain / drift | 5% | Coverage floors ratcheted; contract drift check passes | `./gradlew check`; `./scripts/check-contract-drift.sh` |

**Scoring guide (per dimension):**

- **9–10:** Exit criteria for the relevant Q-step(s) met with verification artifacts.
- **7–8:** Mostly met; minor gaps documented with accepted risk.
- **< 7:** Step not done; do not sign off Q14.

Record scores in a dated table at the bottom of this file or in README "Quality snapshot" when Q14 completes.

### Q14 sign-off (2026-07-20)

**Verdict:** PASSED — every dimension >= 9.0; weighted overall **9.8**.

| Dimension | Weight | Score | Evidence |
|-----------|--------|-------|----------|
| Correctness | 25% | 10.0 | `CI=true ./gradlew clean check` → 307 tests, 0 failures, 0 skipped; `BACKLINE_RUN_PERF_SMOKE=true ./scripts/ci-e2e-demo.sh` EXIT 0 |
| Coverage depth | 20% | 10.0 | JaCoCo: API 86.8% line / 63.7% branch; worker 89.6%; CLI 61.2%; core 82.3% (all above Q6/Q10 floors) |
| Full-stack proof | 15% | 10.0 | E2E Q5a (doctor → run → history → diff → Markdown report) + Q5b (JSON report, LAST_PASSED diff, `--enforce-policy`) + Q13 perf smoke |
| Test rigor | 15% | 9.5 | jqwik properties: assertion evaluation, config URL validation, policy evaluation, DiffService baselines; `CI=true` skipped=0. Accepted risk: PIT remains report-only |
| Security / guardrails | 10% | 9.5 | `./scripts/check-guardrails.sh` pass; `ResponsePreviewPropertiesTest`, redaction constants tests; no secret logging in verified paths |
| Operability | 10% | 9.5 | `DoctorCommandTest` + policy profile / enforce-policy CLI tests; `docs/runbook.md` current |
| Sustain / drift | 5% | 9.0 | Module JaCoCo floors enforced; `./scripts/check-contract-drift.sh` pass. Accepted risk: drift check is grep/heuristic (OpenAPI-generated contract testing remains out of PRD scope) |

**Weighted overall:** \(10.0×0.25 + 10.0×0.20 + 10.0×0.15 + 9.5×0.15 + 9.5×0.10 + 9.5×0.10 + 9.0×0.05) = **9.775 ≈ 9.8**

**Q12:** DROPPED — `PRD.md` was never updated to include persisted baseline preference (`baseline set/show`). Diff continues to use flag-selected baselines (`PREVIOUS_COMPLETED` / `LAST_PASSED`). Does not block sign-off.

**Accepted risks (not defects):**

- Grep-based contract-drift checks (not OpenAPI codegen).
- Mutation testing (PIT) not a CI gate.
- Persisted baseline UX (DROPPED Q12) remains unavailable until an intentional PRD change.
