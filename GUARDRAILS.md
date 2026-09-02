# Backline Guardrails

## Scope

- Build only the rollout compatibility verifier defined by `PRD.md`.
- Keep exactly two primary compatibility verdicts.
- Do not add a server, database, daemon, dashboard, account, hosted control plane, plugin system, deployment action, schema linter, load test, or production-state integration.
- Treat shared services generically through Docker Compose.
- Treat project hooks and workloads as trusted arbitrary programs, not as sandboxed input.

## CLI and configuration

- Expose only `verify`, `validate`, `doctor`, `version`, and help/version aliases.
- Configuration comes from the committed candidate ref before worktrees are created.
- Reject duplicate YAML keys, unknown fields, missing substitutions, invalid targets, invalid directional order, unsafe paths, and reserved `BACKLINE_` variables.
- CLI safety flags may relax policy; YAML may not.
- Commands remain nonempty argument arrays with no implicit shell or textual command concatenation.

## Attribution

- A failed baseline, build, bootstrap, transition, candidate startup, or candidate control is not a compatibility failure by itself.
- Preserve the original base through transition and both base controls.
- Run all three required controls before directional coexistence evidence can pass.
- Continue independent evidence when the environment remains usable; do not add fail-fast behavior.
- Candidate-only or candidate-traffic incompleteness prevents rollback PASS but does not hide a directly observed rollback FAIL.
- Rollback mode is RAW only with no rollback hook and PREPARED whenever rollback hooks are configured.
- Pass wording remains bounded to configured workloads and never claims universal safety or sole causality.

## Docker and machine safety

- Label every owned resource with the current run ID.
- Use unique Compose project/container names and Docker-assigned `127.0.0.1` ports.
- Reject external/global resources, fixed ports, restart policies, replicas, host binds, Docker sockets, privileged containers, host namespaces, devices, unsafe config/secret paths, and non-loopback publications by default.
- Reject nonlocal Docker contexts unless `--allow-remote-docker` is explicit.
- Resolve paths and symlinks before use; reject escapes from candidate worktrees or configured roots.
- Never infer that a reachable resource is disposable.
- Never delete a resource lacking the current run label or an exact registered run path.

## Secrets and artifacts

- Inherit a minimal host environment plus configured allowlisted names.
- Register every nonempty substituted/env-file value for redaction.
- Redact terminal, Markdown, JSON, JSONL, JUnit, build, hook, workload, readiness, and Docker output.
- Persist only redacted resolved configuration; never persist raw normalized Compose interpolation or an environment-file body.
- Bound every captured log and disclose truncation.
- Do not copy arbitrary scenario or shared-handoff files into artifacts.
- Do not send telemetry.

## Process execution

- Use argument arrays and platform-safe process-tree termination.
- Distinguish launch/control failures from launched project failures.
- Capture exit code/signal, timeout, duration, stage, target, and bounded output.
- Do not hide retries; scenario repeats are explicit and sequential.

## Cleanup

- Cleanup runs after success, non-pass, timeout, interruption, and internal error.
- `--keep-on-failure` retains only a non-passing current run and prints exact cleanup commands.
- Verify cleanup rather than assuming command success.
- Cleanup errors remain separate and never overwrite compatibility evidence.
- Never prune prior artifact directories automatically.

## Testing

- Unit-test deterministic config, state, verdict, redaction, rendering, and safety rules.
- Integration-test real Git and Docker behavior at their boundaries.
- Linux CI runs required Docker tests without silent skips; unit tests run on Linux, macOS, and Windows.
- Mandatory E2E fixtures prove safe, mixed-version failure, raw rollback failure, and prepared rollback behavior.
- Security tests cover secrets, traversal/symlinks, unsafe Compose, remote Docker, loopback binding, fork trust, and cleanup ownership.

## Documentation

- Document intent and non-obvious constraints, especially configuration, attribution, process semantics, redaction, Compose safety, and cleanup.
- Do not comment obvious code or leave TODOs in required execution paths.
- README and site claims must match observed demo output.

## Definition of done

The active tree contains the native Go CLI, required demos, schemas, documentation, site, CI, and release automation; it contains no active Java regression-ledger product. All PRD acceptance criteria have a passing check or documentation artifact.
