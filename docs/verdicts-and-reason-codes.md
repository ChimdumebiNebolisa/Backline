# Verdicts, outcomes, reason codes, and exits

Primary verdicts are `PASS`, `FAIL`, or `INCONCLUSIVE`. Overall status is `PASS`, `FAIL`, `INCONCLUSIVE`, or `ERROR`. Stage outcomes are `PASS`, `FAIL`, `ERROR`, `SKIPPED`, or `INCOMPLETE`.

Direct mixed-version or rollback failure evidence takes precedence over later uncertainty or operational errors. Operational and cleanup state remain separately visible in artifacts.

## Exit codes

| Code | Meaning |
| ---: | --- |
| `0` | Both compatibility verdicts pass and cleanup succeeds. |
| `10` | One or both compatibility verdicts fail. |
| `20` | No verdict fails, but required project evidence is inconclusive. |
| `21` | Configuration or an isolation safeguard is invalid. |
| `22` | A required local prerequisite is unavailable before verification. |
| `23` | Git, Docker, Compose, filesystem, process launch/control, or orchestration failed. |
| `24` | Unexpected internal Backline error. |
| `25` | No higher-priority failure occurred, but cleanup failed. |

## Reason codes

```text
NONE
CONFIG_INVALID
SAFETY_POLICY_BLOCKED
PREREQUISITE_UNAVAILABLE
BUILD_FAILED
SHARED_ENVIRONMENT_FAILED
BOOTSTRAP_FAILED
BASE_STARTUP_FAILED
BASELINE_FAILED
TRANSITION_HOOK_FAILED
BASE_LOST_READINESS
BASE_AFTER_TRANSITION_CONTROL_FAILED
CANDIDATE_STARTUP_FAILED
CANDIDATE_CONTROL_FAILED
BASE_WITH_CANDIDATE_CONTROL_FAILED
COEXISTENCE_SCENARIO_FAILED
REQUIRED_COMPONENT_EXITED
COMMAND_LAUNCH_FAILED
CANDIDATE_ONLY_HOOK_FAILED
CANDIDATE_TRAFFIC_FAILED
CANDIDATE_TRAFFIC_INCOMPLETE
NO_CANDIDATE_MUTATION_SCENARIO_RAN
ROLLBACK_HOOK_FAILED
BASE_RESTART_FAILED
ROLLBACK_SCENARIO_FAILED
INTERRUPTED
ORCHESTRATION_ERROR
INTERNAL_ERROR
CLEANUP_FAILED
```

Machine-readable definitions are versioned by [summary-v1.schema.json](../schemas/summary-v1.schema.json) and [event-v1.schema.json](../schemas/event-v1.schema.json).
