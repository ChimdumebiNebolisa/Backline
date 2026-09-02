# Rollout demo

The demo creates a temporary two-commit Git repository, starts an isolated PostgreSQL Compose service, builds base and candidate release images, and asserts exact verdicts, all three controls, rollback mode, reason codes, process exit, schemas, loopback-only ports, redaction, and leak-free cleanup.

Run all public cases:

```powershell
./examples/rollout-demo/run.ps1
```

```bash
bash ./examples/rollout-demo/run.sh
```

Pass `safe`, `mixed-failure`, or `rollback-failure` to run one public case:

| Case | Mixed version | Rollback | Exit |
| --- | --- | --- | ---: |
| `safe` | `PASS` | `PASS [RAW]` | `0` |
| `mixed-failure` | `FAIL` / `COEXISTENCE_SCENARIO_FAILED` | `PASS [RAW]` | `10` |
| `rollback-failure` | `PASS` | `FAIL [RAW]` / `ROLLBACK_SCENARIO_FAILED` | `10` |

Advanced cases:

```powershell
./examples/rollout-demo/run.ps1 -Case prepared-rollback
./examples/rollout-demo/run.ps1 -Case handoff
./examples/rollout-demo/run.ps1 -Case candidate-only-failure
```

```bash
bash ./examples/rollout-demo/run.sh prepared-rollback
bash ./examples/rollout-demo/run.sh handoff
bash ./examples/rollout-demo/run.sh candidate-only-failure
```

These verify prepared rollback, nonartifact cross-stage handoff, and candidate-only failure dependency/precedence behavior. Set `BACKLINE_DEMO_ARTIFACT_ROOT` to retain redacted run evidence outside each temporary repository.
