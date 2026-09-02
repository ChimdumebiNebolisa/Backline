# Workload authoring

Backline treats a workload as trusted project code whose process exit status is the assertion interface: zero passes, nonzero or timeout is a project-controlled failure, and failure to launch or control the process is operational.

## Command contract

```yaml
steps:
  - name: candidate reads base record
    target: candidate.api
    command: [python, scripts/check_record.py, base-record]
    cwd_revision: candidate
    working_directory: tests/rollout
    timeout_seconds: 45
    environment:
      ASSERT_MODE: strict
```

Backline never inserts a shell. Call `bash`, `sh`, or `powershell` explicitly when shell behavior is required. Keep commands deterministic and repeatable because controls may rerun baseline scenarios against evolving shared state.

## Target variables

For a targeted host step, Backline provides the selected component's loopback endpoint through `BACKLINE_TARGET_URL` and related `BACKLINE_*` variables. Commands are never rewritten during control reuse; only target mappings and Backline-supplied endpoint values change.

## Directories

- `BACKLINE_SCENARIO_DIR` is writable and isolated to one scenario.
- `BACKLINE_SHARED_DIR` is writable across hooks and stages for nonsecret record IDs and coordination data.
- `BACKLINE_ARTIFACT_DIR` identifies the run artifact directory.

Scenario and handoff contents are not copied into artifacts. They are not secret stores; project code can still print or transmit their contents.

## Environment

Declare tool dependencies in `security.passthrough_environment`. For example, a Windows workload using `go run` may need `LOCALAPPDATA` for Go's build cache. Put sensitive variable names in `redact_environment` and avoid printing secrets even though Backline applies best-effort redaction.

## Scenario design

- Baseline proves base-only behavior.
- Controls must be safe to rerun.
- `base_to_candidate` scenarios contain a base target before a candidate target.
- `candidate_to_base` scenarios contain a candidate target before a base target.
- Alternating scenarios exercise both roles.
- Candidate traffic represents post-cutover traffic and includes at least one declared mutating scenario.
- Rollback checks run against fresh base containers without restoring shared state.
