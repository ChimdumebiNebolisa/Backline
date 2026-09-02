# Safety and trust

Backline is designed for trusted project code in an isolated local or CI Docker environment.

## Default safeguards

- committed candidate configuration and confined Git paths
- detached run-owned worktrees
- argument-array process execution with timeouts and process-tree termination
- local Docker context requirement
- Compose normalization and unsafe-feature rejection
- Docker-assigned loopback-only application ports
- exact resource registry plus run labels
- cleanup on success, failure, timeout, interruption, and panic
- bounded redacted terminal and artifact output

`--keep-on-failure` retains only a nonpassing run and prints exact resource-specific cleanup commands. Passing runs are never retained. Unsafe opt-ins relax validation only; they never broaden what cleanup may delete.

## Trusted-code boundary

Hooks and workloads can execute arbitrary programs and make network calls. Dockerfiles can run arbitrary build steps. Backline cannot contain hostile code, classify every secret, or prove a repository safe. Review candidate changes before execution.

## Secret handling

Register sensitive variable names and avoid printing secrets. Backline also recognizes common credential patterns, bounds logs, and redacts resolved config, Markdown, JSON, JSONL, JUnit, build output, readiness output, and Docker logs. Redaction is best effort, not a security boundary.
