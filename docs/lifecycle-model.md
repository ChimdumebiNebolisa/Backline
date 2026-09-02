# Lifecycle model

Backline executes one explicit sequential state machine:

```text
preflight -> build -> shared environment -> bootstrap -> base startup
          -> baseline -> transition -> base control
          -> candidate startup -> candidate control -> second base control
          -> coexistence -> base stop -> candidate-only hooks
          -> candidate traffic -> candidate stop -> rollback hooks
          -> fresh base startup -> rollback checks -> reporting -> cleanup
```

The active checkout is never modified. Base and candidate use detached run-owned worktrees and immutable labeled images. Both revisions connect to one candidate-defined Compose environment.

Independent evidence continues after project failures when the environment remains usable. Dependent stages are skipped explicitly. A failed candidate-only hook skips representative candidate traffic but can proceed to direct rollback diagnostics. Interruption, timeout, panic, and ordinary failure all enter centralized cleanup.

The original base process survives transition and both base controls. Base stops before candidate-only hooks. Candidate stops before rollback. Rollback always starts fresh base containers and never silently restores shared services or volumes.
