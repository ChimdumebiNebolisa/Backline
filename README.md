# Backline

Backline tests the part of a deployment your normal test suite misses: the base and candidate revisions running against the same state, including whether the base revision still works after rollback.

It answers two bounded questions:

1. Did the configured base and candidate workloads remain compatible while both revisions were running?
2. After candidate cutover and traffic, did fresh base processes still pass the configured rollback checks?

```bash
backline verify
```

A `PASS` means Backline detected no incompatibility in the configured controls and scenarios. It is not a proof that every production behavior is safe.

## Requirements

- Git
- Docker Engine or Docker Desktop
- Docker Compose v2
- A trusted repository with a committed `backline.yml`

Backline itself is one Go binary. It has no server, account, daemon, database, Java, Node.js, or Python runtime requirement. Project-owned hooks and workloads may require their own tools.

## Install

Download a pinned release archive and `SHA256SUMS` from GitHub Releases, then verify it before extracting:

```bash
sha256sum --check SHA256SUMS
tar -xzf backline-vX.Y.Z-linux-amd64.tar.gz
install backline "$HOME/.local/bin/backline"
```

PowerShell:

```powershell
Get-FileHash .\backline-vX.Y.Z-windows-amd64.zip -Algorithm SHA256
# Compare the printed digest with SHA256SUMS, then extract backline.exe.
```

Or build the current module with Go 1.26:

```bash
go install github.com/ChimdumebiNebolisa/Backline/cmd/backline@latest
```

## Minimal configuration

Configuration is read from the resolved candidate commit, not from uncommitted checkout contents. This abbreviated example shows the required shape; start from the complete [rollout demo configuration](examples/rollout-demo/fixture/backline.yml) and see the [configuration reference](docs/config-reference.md).

```yaml
version: 1
revisions:
  base: origin/main

shared_environment:
  compose_file: compose.yml
  services: [postgres]

release:
  components:
    - name: api
      build:
        context: .
        dockerfile: Dockerfile
      run:
        internal_port: 8080
        readiness:
          type: http
          path: /health
          expected_status: 200

workloads:
  defaults:
    cwd_revision: candidate
  baseline: # project command scenarios
  controls: # all three attribution controls
  coexistence: # base_to_candidate, candidate_to_base, alternating
  candidate_traffic: # includes a mutates_state: true scenario
  rollback: # reuse_baseline or explicit scenarios
```

## Quick start

From a clean checkout with Docker running:

```bash
go build -o backline ./cmd/backline
./backline validate
./backline doctor
./backline verify
```

PowerShell:

```powershell
go build -o backline.exe .\cmd\backline
.\backline.exe validate
.\backline.exe doctor
.\backline.exe verify
```

Backline performs preflight before it creates resources, builds detached base and candidate worktrees sequentially, starts one candidate-defined Compose environment, and binds addressable components only to Docker-assigned `127.0.0.1` ports.

## What runs

The lifecycle is deliberate:

1. Base-only baseline.
2. Candidate transition while the original base remains alive.
3. Base-after-transition control using that original process.
4. Candidate startup and candidate-before-coexistence control.
5. Base-with-candidate-running control, again using the original base.
6. Directional coexistence scenarios.
7. Base shutdown, candidate-only cutover hooks, and candidate traffic.
8. Candidate shutdown, optional rollback preparation, fresh base startup, and rollback checks.

The three controls reduce false attribution. They do not prove causality. See [control workloads](docs/control-workloads.md) and the [lifecycle model](docs/lifecycle-model.md).

## Observed demo output

Safe rollout:

```text
Controls
  PASS         base after transition
  PASS         candidate before coexistence
  PASS         base with candidate running

Mixed-version compatibility
  PASS         No incompatibility detected in the configured mixed-version controls and scenarios.

Rollback compatibility [RAW]
  PASS         The base revision passed the configured rollback checks directly against state left by candidate cutover and traffic.

Result: PASS
```

Raw rollback failure:

```text
Mixed-version compatibility
  PASS         No incompatibility detected in the configured mixed-version controls and scenarios.

Rollback compatibility [RAW]
  FAIL         The base revision failed against state left by candidate cutover and traffic.
  Reason: ROLLBACK_SCENARIO_FAILED

Result: FAIL
```

Run the deterministic fixtures with [Bash or PowerShell](examples/rollout-demo/README.md):

- `safe`: mixed `PASS`, raw rollback `PASS`.
- `mixed-failure`: controls pass, then coexistence fails.
- `rollback-failure`: mixed passes, candidate traffic writes incompatible state, fresh base fails.
- `prepared-rollback`, `handoff`, and `candidate-only-failure`: advanced lifecycle coverage.

## Raw and prepared rollback

- `RAW` means fresh base processes are checked directly against state left by candidate cutover and traffic.
- `PREPARED` means configured rollback hooks transformed or prepared state before fresh base processes were checked.

The mode is always disclosed. A prepared pass is a claim about the configured rollback procedure, not untouched candidate-written state. See [rollback modes](docs/rollback-modes.md).

Candidate-only hooks run after base stops and before candidate traffic. Components may also define common, base-specific, and candidate-specific environment values without duplicating component definitions.

## Workload handoff

Host hooks and workloads receive `BACKLINE_SHARED_DIR`, a run-scoped writable directory for nonsecret identifiers such as record IDs. Scenario steps also receive isolated scenario directories. Backline does not copy either directory into artifacts and does not treat them as secret stores.

See [workload authoring](docs/workload-authoring.md).

## Results and artifacts

Verdicts are `PASS`, `FAIL`, or `INCONCLUSIVE`. Overall status can also be `ERROR`. Stage outcomes are `PASS`, `FAIL`, `ERROR`, `SKIPPED`, or `INCOMPLETE`.

Each run writes schema-version-1 artifacts under `.backline/runs` by default:

- `report.md`
- `summary.json`
- `events.jsonl`
- `resolved-config.redacted.yml`
- bounded logs and build evidence
- optional JUnit XML

Use `--json-output` or `--junit-output` to copy machine-readable results to an explicit path. Full reason-code and exit-code semantics are documented in [verdicts and reason codes](docs/verdicts-and-reason-codes.md).

## CI

```bash
backline verify \
  --json-output build/backline/summary.json \
  --junit-output build/backline/junit.xml \
  --artifact-dir build/backline/runs
```

Configure CI to upload `build/backline/**` with `if: always()` so partial and failing evidence is preserved. Upload `junit.xml` with the CI provider's JUnit reporter. See [CI integration](docs/ci-integration.md) and [CI security](docs/ci-security.md).

## Trust and safety boundary

Backline builds and executes repository hooks and workloads. That code is trusted; Backline is not a sandbox. Docker access is effectively host-level privilege on many systems. Do not run untrusted fork revisions with Docker credentials, repository secrets, or privileged runners.

By default Backline rejects remote Docker contexts, external resources, host bind mounts, Docker socket mounts, privileged containers, host namespaces, devices, escaping config/secret files, and non-loopback published ports. Unsafe flags require visible operator opt-in and never widen cleanup ownership.

Read [safety and trust](docs/safety-and-trust.md), [known limitations](docs/known-limitations.md), and [troubleshooting](docs/troubleshooting.md) before adopting it in CI.

## Commands

```text
backline verify [flags]
backline validate [flags]
backline doctor [flags]
backline version
backline help [command]
```

Run `backline help <command>` for the exact flag contract.

## Project documentation

- [Configuration reference](docs/config-reference.md)
- [Workload authoring](docs/workload-authoring.md)
- [Control workloads](docs/control-workloads.md)
- [Lifecycle model](docs/lifecycle-model.md)
- [Rollback modes](docs/rollback-modes.md)
- [Verdicts and reason codes](docs/verdicts-and-reason-codes.md)
- [Architecture](docs/architecture.md)
- [CI integration](docs/ci-integration.md)
- [CI security](docs/ci-security.md)
- [Safety and trust](docs/safety-and-trust.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Known limitations](docs/known-limitations.md)
- [Acceptance trace](docs/acceptance-trace.md)

The previous API regression ledger is preserved by annotated tag `legacy/api-regression-ledger-v1`.

## License

MIT
