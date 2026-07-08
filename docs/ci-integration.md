# CI integration guide

Backline supports CI-friendly outputs for regression policy checks.

## Policy-aware run command

Configure thresholds in `backline.yml`:

```yaml
policy:
  max_newly_failing: 0
  max_errored_checks: 0
  max_latency_regression_ms: 200
```

Then execute:

```bash
backline run --enforce-policy --junit-output ./build/backline-policy.xml
```

Behavior:

- Exit code `0`: run completed and policy passed.
- Exit code `5`: run reached terminal status but policy failed.
- Exit code `1/2/3/4`: API/runtime/validation timeout paths (non-policy failures).

## Baseline selection

Use diff baseline options to control comparison target:

```bash
backline run --enforce-policy --baseline PREVIOUS_COMPLETED
backline run --enforce-policy --baseline LAST_PASSED
backline run --enforce-policy --baseline FIXED_RUN --baseline-run-id <runId>
```

The same options are available on `backline diff`.

## GitHub Actions template

A starter workflow lives at `.github/workflows/backline-ci.yml`.
It runs `./gradlew clean test` and uploads JUnit artifacts for module tests.
