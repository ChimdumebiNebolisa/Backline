# Architecture

Backline is a single Go CLI around explicit Git, Docker, Compose, filesystem, and project-process boundaries. It has no server-side component or Backline-owned database.

```text
committed candidate config -> detached base/candidate worktrees -> immutable images
                           -> one shared Compose environment
                           -> baseline, controls, coexistence, cutover, rollback
                           -> versioned redacted artifacts -> owned-resource cleanup
```

The CLI dispatcher lives under `internal/cli`; configuration and preflight under `internal/config`, `internal/gitops`, and `internal/preflight`; process and Docker boundaries under `internal/process`, `internal/dockerops`, and `internal/compose`; lifecycle execution under `internal/components`, `internal/hooks`, `internal/workloads`, and `internal/orchestrator`; classification and artifacts under `internal/verdicts`, `internal/reporting`, `internal/redact`, `internal/artifacts`, and `internal/cleanup`.

Commands use argument arrays and injectable runners. The orchestrator is sequential and state-machine driven. Stage evidence, verdict classification, operational errors, and cleanup status are separate models. The classifier is pure and gives direct compatibility failures precedence over later uncertainty.

See the governing [root architecture](../ARCHITECTURE.md), [guardrails](../GUARDRAILS.md), and [PRD](../PRD.md).
