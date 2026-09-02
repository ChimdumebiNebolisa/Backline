# Backline Architecture

## Purpose

Backline is a single-process Go CLI that verifies mixed-version and rollback compatibility for trusted Git revisions of Dockerized stateful services. `PRD.md` is the product scope; this document fixes ownership boundaries.

## System shape

```text
committed candidate config -> isolated worktrees -> immutable images
              -> one run-scoped Compose environment
              -> baseline / transition / controls / coexistence
              -> candidate-only cutover / traffic / rollback
              -> verdicts / artifacts / verified cleanup
```

Backline owns no server, database, daemon, account, dashboard, or long-term history. Git, Docker, Docker Compose v2, the filesystem, and project commands are external boundaries.

## Package ownership

| Package | Responsibility |
| --- | --- |
| `cmd/backline` | Process entry point and build-time version value. |
| `internal/cli` | Command dispatch, flags, help, terminal output, and exit codes. |
| `internal/app` | Command use cases and assembly of preflight and verification dependencies. |
| `internal/model` | Stable run, stage, verdict, reason-code, process-result, and artifact types. |
| `internal/config` | Committed-candidate configuration loading, strict YAML parsing, defaults, substitution, and semantic validation. |
| `internal/gitops` | Repository discovery, ref resolution, Git-object reads, and detached worktrees. |
| `internal/preflight` | Revision/config resolution, path validation, Compose normalization, prerequisite checks, and worktree preparation. |
| `internal/process` | Argument-array command execution, bounded capture, timeouts, cancellation, and process-tree termination. |
| `internal/dockerops` | Docker context checks, image builds, container inspection, labels, ports, and component lifecycle. |
| `internal/compose` | Compose normalization, safety validation, shared-service startup, health, networks, volumes, and shutdown. |
| `internal/components` | Revision-role component startup, environment merge, readiness, monitoring, and stop behavior. |
| `internal/hooks` | Host and one-off component lifecycle hooks. |
| `internal/workloads` | Host workload steps, scenario directories, target variables, repeats, and control reuse. |
| `internal/orchestrator` | The deterministic lifecycle state machine and dependency/continuation decisions. |
| `internal/verdicts` | Pure stage-to-verdict classification, reason precedence, overall status, and process exit selection. |
| `internal/redact` | Exact-value and pattern-based redaction for every persisted or displayed channel. |
| `internal/artifacts` | Secure run directories, JSON, JSONL, redacted YAML, bounded logs, and atomic external artifact writes. |
| `internal/reporting` | Terminal, Markdown, JUnit, and GitHub step-summary rendering. |
| `internal/cleanup` | Run-owned resource registry, signal cleanup, retention, cleanup verification, and manual commands. |

Packages depend on `internal/model` and narrow interfaces. Orchestration coordinates packages but does not absorb their implementation rules. Docker, Compose, Git, and process calls remain injectable for deterministic tests.

## Lifecycle and state ownership

The orchestrator owns this ordered lifecycle:

1. preflight and committed candidate configuration;
2. run directories and detached worktrees;
3. base and candidate image builds;
4. one shared Compose environment;
5. base bootstrap, startup, and baseline;
6. candidate transition and original-base control;
7. candidate startup/control and second original-base control;
8. directional mixed-version scenarios;
9. base stop, candidate-only hooks, and candidate traffic;
10. candidate stop, optional rollback preparation, fresh base, and rollback checks;
11. verdict finalization, artifact rendering, and cleanup.

Shared services start once and their state is never silently reset. The original base containers survive transition and both attribution controls. Rollback always uses fresh base containers.

Stage outcomes are evidence, not verdicts. `internal/verdicts` is the only owner of compatibility classification and exit-code precedence.

## External command boundary

- Commands are argument arrays; Backline never adds an implicit shell.
- Every call has context cancellation and an operation timeout.
- A launched nonzero/timeout is project evidence; failure to launch/control is operational.
- Logs are bounded and redacted before terminal or artifact output.
- Workload commands inherit only required OS variables, explicitly allowed variables, and Backline-reserved variables.

## Filesystem boundary

- Candidate config is read from Git objects before worktree creation.
- Repository/worktree-relative paths are normalized, symlinks resolved, and escapes rejected.
- Explicit CLI output/environment paths may be external; configured relative paths remain within the invoking repository.
- Cleanup removes only exact registered run paths and never broad roots or unresolved paths.

## Docker boundary

- Every owned resource has a run ID label and deterministic role/component metadata.
- Compose uses a unique project name. Candidate YAML cannot enable unsafe behavior.
- Addressable components use Docker-assigned random loopback ports.
- External resources, host mounts, global names, fixed ports, restart/replica settings, privileged/host namespaces, devices, Docker sockets, and remote contexts are rejected unless an explicit CLI safety flag applies.
- Cleanup never deletes an external, unlabeled, or differently labeled resource.

## Artifacts

Artifacts are run-local and schema-versioned. The canonical run directory contains `report.md`, `summary.json`, `events.jsonl`, `resolved-config.redacted.yml`, and bounded logs. Scenario and shared handoff directories are execution state and are not copied into artifacts.

Artifact rendering is best effort after partial execution. Compatibility evidence is never overwritten by cleanup or reporting errors.

## Trust model

Backline builds and executes trusted base/candidate revisions and project commands. It is not a sandbox. Docker access is privileged. Standard CI never runs candidate code through `pull_request_target`, never gives secrets to untrusted forks, and gates Docker execution for untrusted contributions.

## Rejected alternatives

- Preserving the Java API/worker/database architecture.
- Adding an embedded database or history service.
- Implementing framework-specific database, cache, or broker engines.
- Adding an HTTP workload DSL, plugin process, Kubernetes support, or production deployment behavior.
- Using a Docker SDK where the required Docker/Compose CLI boundary already provides the contract.
