# Configuration reference

Backline reads strict YAML `version: 1` from the resolved candidate Git object before creating worktrees. Duplicate keys, unknown fields, extra YAML documents, missing substitutions, invalid paths, unsafe Compose features, and inconsistent workload targets are errors.

Use the complete [demo configuration](../examples/rollout-demo/fixture/backline.yml) as a starting point.

## Top-level sections

| Section | Purpose |
| --- | --- |
| `version` | Required schema version; currently `1`. |
| `revisions` | Base ref and clean-checkout policy. The CLI candidate defaults to `HEAD`. |
| `artifacts` | Run directory, log bound, and optional JUnit behavior. |
| `shared_environment` | Candidate-defined Compose file, selected services, optional env file, startup and shutdown timeouts. |
| `security` | Host variable allowlist and names whose values must be registered for redaction. |
| `release.components` | Components that exist in both revisions, image build inputs, commands, environment, ports, and readiness. |
| `lifecycle` | Ordered bootstrap, transition, candidate-only, and rollback hooks. |
| `workloads` | Defaults, baseline, all controls, directional coexistence, candidate traffic, and rollback checks. |

## Defaults and bounds

- clean checkout required: `true`
- artifact directory: `.backline/runs`
- maximum log bytes per command: 1 MiB
- command timeout: 60 seconds
- component readiness timeout: 90 seconds
- shared-service startup timeout: 120 seconds
- shutdown timeout: 30 seconds
- scenario repeats: 1; allowed range 1-100
- positive timeouts: at most 3600 seconds

## Components and readiness

Each component declares a build context and Dockerfile that exist in both revisions. `run.environment` is common; `base_environment` and `candidate_environment` override it by role. Addressable components declare one `internal_port`; Backline publishes it on a random `127.0.0.1` host port. At least one addressable component is required.

Readiness types are HTTP, TCP, Docker health, or command. HTTP readiness includes path and expected status. Docker-health readiness requires an image health check. Command readiness executes an explicit command array without an implicit shell.

## Hooks and workloads

Commands are nonempty argument arrays. Host working directories remain inside the selected revision worktree. Component hooks run in one-off containers and may access only `/backline/shared` read/write and `/backline/artifacts` read-only.

Workload targets use `<role>.<component>`, for example `base.api`. Directional coexistence scenarios must contain targets in the declared role order. Candidate traffic targets candidate components and at least one scenario declares `mutates_state: true`.

## Environment handling

Only `${NAME}` substitution is supported. Invoking-process variables override env-file values for substitution. User maps and passthrough lists cannot set reserved `BACKLINE_*` names.

Host commands receive only required OS variables, `security.passthrough_environment`, configured values, and Backline variables. Register sensitive names under `redact_environment`. Every nonempty env-file value is registered for redaction automatically.

## Compose safety

Backline normalizes Compose with `docker compose config --format json`. By default it rejects unselected transitive services, missing health checks, global names, replicas other than one, unsafe restart policies, bind mounts, Docker sockets, host namespaces, devices, privileged mode, escaping configs/secrets, external resources, and non-loopback publications.

`--allow-unsafe-compose` relaxes validation with a warning. It does not expand cleanup ownership. `--allow-remote-docker` is CLI-only and should be used only with a trusted disposable endpoint.

## CLI precedence

CLI flags override matching configuration values. `--candidate-ref` is resolved first; configuration is then loaded from that commit. `--base-ref`, `--env-file`, and `--artifact-dir` override their configured values.
