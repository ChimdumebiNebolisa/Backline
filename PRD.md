# Backline Product Requirements Document

## Rollout Compatibility Verifier

**Status:** Final replacement build scope
**Document version:** 1.2
**Reviewed:** 2026-09-01
**Product name:** Backline
**Tagline:** Mixed-version and rollback checks for stateful services

---

## Revision history

| Version | Date | Summary |
| --- | --- | --- |
| 1.0 | 2026-09-01 | Initial replacement scope for rollout compatibility verification. |
| 1.1 | 2026-09-01 | Reviewed for implementability, configuration-source semantics, Docker isolation, CI trust boundaries, networking, artifact safety, and internal consistency. |
| 1.2 | 2026-09-01 | Added three attribution controls, candidate-only lifecycle hooks, role-specific component environment overrides, raw and prepared rollback semantics, cross-stage handoff support, stronger Compose isolation, reproducibility metadata, directional scenario validation, and corrected verdict and exit-code semantics. |

### Review corrections adopted in 1.2

This review preserves the product thesis and the two primary verdicts. It corrects the parts that could otherwise produce misleading results or unsafe implementation choices:

- A valid base baseline is no longer enough to attribute every later failure to version interaction. Backline now runs a base-after-transition control, a candidate-before-coexistence control, and a base-with-candidate-running control.
- Transition or candidate correctness failures no longer masquerade as compatibility failures. They produce failed stage outcomes and inconclusive compatibility verdicts unless direct compatibility evidence exists.
- Rollback output now declares whether it is **raw** or **prepared**. A rollback hook is allowed to transform state, so a prepared pass cannot claim that the base read untouched candidate-written state.
- Candidate-only lifecycle hooks can model cutover work such as backfills or contract migrations after base processes stop and before representative candidate traffic.
- Components may define common plus base-specific and candidate-specific environment values without duplicating component definitions.
- Required directional scenarios must contain the declared role order inside each scenario, not merely somewhere in the workload group.
- `--fail-fast` was removed because the product is required to collect both compatibility answers in one run whenever the environment remains usable.
- Automatic successful-artifact retention and pruning were removed. Backline produces artifacts; the user or CI system owns retention policy.
- A run-scoped shared handoff directory was added so workloads can pass record IDs and other nonsecret coordination data across stages without creating another storage service.
- Exit codes now distinguish inconclusive project evidence from operational orchestration failure.
- Host command execution, forked CI, Docker access, redacted configuration, Compose health checks, and path confinement have explicit requirements.
- Compose global names, fixed host ports, restart policies, replicas, and host-path inputs are constrained so a supposedly ephemeral run cannot silently reuse or expose host resources.

---

## 1. Document rule

This PRD replaces the existing Backline product definition.

It defines one complete build. It is not a staged roadmap. Every capability marked as required belongs to the same final version. Features not listed here must not be added unless this document is deliberately revised before implementation.

The existing API regression ledger implementation must be preserved through Git history or a legacy tag, but it must not constrain the new architecture. The active product must be designed around rollout compatibility, not around preserving old code.

---

## 2. Product summary

Backline is a local-first command-line tool that tests software transitions rather than testing each software version in isolation.

It builds a **base revision** and a **candidate revision**, runs both against the same ephemeral shared services, executes configured controls and workloads across the rollout lifecycle, and returns two primary verdicts:

1. **Mixed-version compatibility:** Did the configured scenarios detect a failure attributable to the rollout transition or to base and candidate operation against the same evolving state?
2. **Rollback compatibility:** After the candidate revision handled traffic and mutated shared state, did a fresh instance of the base revision satisfy the configured rollback procedure and checks?

Backline is designed for stateful, Dockerized services that use rolling deployments or require rollback confidence.

Backline does not claim to prove that two releases are universally safe. A passing result means:

> No incompatibility was detected across the configured controls and rollout scenarios.

A rollback result is labeled **RAW** when no rollback preparation hook runs and **PREPARED** when project-supplied rollback preparation runs before the base restarts.

---

## 3. Problem statement

Normal test suites usually answer questions such as:

- Does the base revision work?
- Does the candidate revision work?
- Does the candidate revision satisfy its unit and integration tests?
- Does the declared API or database schema contain an obvious breaking change?

Those checks can all pass while a deployment still fails.

A rollout can introduce failures that exist only between versions:

- The candidate writes a database value the base revision cannot parse.
- The candidate serializes a cache entry the base revision cannot deserialize.
- The candidate publishes a message the base worker cannot process.
- A schema transition leaves an already-running base process using invalid assumptions.
- Both revisions work independently, but fail when they share mutable state.
- The candidate works after deployment, but rollback to the base revision fails because the candidate already changed persisted state.

The missing test is not merely whether version N works. It is whether version N and version N-1 can safely overlap and whether N-1 can return after N has changed the world around it.

Backline exists to exercise that transition deliberately before production.

---

## 4. Core product questions

Every completed Backline run must attempt to answer both questions. A verdict may be `INCONCLUSIVE` when required evidence could not be established.

### 4.1 Mixed-version compatibility

> Are the base and candidate revisions compatible while they run together against the same shared state?

The configured coexistence workloads must include both directions:

- Base writes or acts, then candidate reads or reacts.
- Candidate writes or acts, then base reads or reacts.

Before those scenarios run, Backline must establish that the base still works after the candidate transition, that the candidate can satisfy a control workload, and that the original base still works after candidate startup and control activity.

Optional alternating scenarios may exercise longer ordered sequences across both revisions.

### 4.2 Rollback compatibility

> After the candidate revision has handled representative traffic and mutated shared state, can a fresh base revision start and operate successfully?

Rollback verification must not silently restore shared services to their original state.

- **Raw rollback:** No rollback lifecycle hook runs. The base is tested directly against the state left by candidate cutover and traffic.
- **Prepared rollback:** One or more explicit rollback lifecycle hooks run before the base starts. The verdict covers the configured rollback procedure, not untouched candidate-written state.

Backline must disclose the rollback mode in terminal output and all artifacts.

---

## 5. Locked product decisions

The following decisions are final for this build:

- Backline is a **single command-line application**, not a server.
- Backline owns no database and stores no long-term run history.
- Docker Compose is the boundary for shared services.
- Shared services are treated generically. Backline does not implement separate PostgreSQL, Redis, Kafka, or RabbitMQ engines.
- The selected shared-service definitions come from the committed candidate test harness and remain fixed for the entire run. Shared-service version upgrades are not modeled.
- Base and candidate application components are built from Git revisions.
- The candidate revision defaults to `HEAD`; configuration is read from that committed candidate revision, not from uncommitted files in the active checkout.
- At least one release component must be addressable from a host workload. Headless components may participate, but Backline does not generically route a message to a specific headless consumer.
- Workloads are executable commands supplied by the project. Backline does not invent another API-testing language.
- A process that starts and exits with code `0` passes. A process that starts and exits nonzero reports a project-controlled failure. Failure to launch a process is operational or configuration failure, not proof of incompatibility.
- A base-after-transition control, a candidate-before-coexistence control, and a base-with-candidate-running control are required to reduce false attribution.
- Backline returns exactly two primary compatibility verdicts, plus stage outcomes and stable machine-readable reason codes.
- Base components remain alive during the candidate transition, control checks, and mixed-version stage unless a detected failure makes that impossible.
- After coexistence, candidate-only lifecycle hooks may model cutover work before candidate traffic. Rollback then stops the candidate and starts fresh base components against state reached after candidate cutover, traffic, and any explicit rollback preparation.
- Rollback mode is always reported as `RAW` or `PREPARED`.
- Backline attempts to collect both primary answers in one run whenever the environment remains usable. There is no fail-fast mode.
- Backline does not automatically prune prior artifacts. File retention belongs to the invoking user or CI system.
- Backline runs locally and in CI.
- Backline is distributed as a single native CLI binary implemented in Go.
- The Backline binary itself must not require Java, Node.js, Python, a Backline server, or a Backline account.
- Docker, Docker Compose v2, and Git are the only mandatory external runtime dependencies for Backline. Project-owned hooks and workloads may require tools already used by that project.
- Addressable application components are published only on Docker-assigned random loopback ports.
- Safety overrides are explicit CLI flags. Candidate-controlled YAML cannot silently disable isolation safeguards.
- Backline builds and executes project code and must be used only with trusted repositories and trusted revisions.
- True concurrency, load testing, production traffic capture, and shared-service version upgrades are out of scope.
- A passing result is bounded evidence, not proof of universal compatibility.

---

## 6. Target user

### Primary user

A backend, platform, infrastructure, or site reliability engineer responsible for a stateful service that:

- is deployed through rolling replacement, canary deployment, blue-green deployment, or another overlap-based process;
- may need to roll back after the new release has handled traffic;
- uses shared mutable infrastructure such as a database, cache, message broker, object-store emulator, or another Compose-managed state service;
- already has executable smoke tests, integration tests, scripts, or API workflows that can be used as Backline workloads.

### Secondary user

An application developer preparing a stateful release who wants a deterministic local or CI rehearsal of version overlap and rollback.

### Non-target user

Backline is not designed primarily for:

- stateless static sites;
- frontend visual regression testing;
- interactive API exploration;
- production monitoring;
- load testing;
- teams without any representative executable workload.

---

## 7. Goals

Backline must:

1. Run the complete verification from one command: `backline verify`.
2. Build the base and candidate revisions in isolated Git worktrees.
3. Start one isolated set of shared services and preserve their state through the full run.
4. Run the base revision alone and establish a valid baseline.
5. Apply the configured candidate transition while the original base processes remain alive.
6. Recheck the original base after transition without restarting it.
7. Start the candidate and run a candidate control.
8. Recheck the original base while candidate processes are active before interpreting directional failures as compatibility evidence.
9. Run base and candidate components together against the same shared state.
10. Exercise both cross-version directions in explicit per-scenario order.
11. Stop base components and execute optional candidate-only cutover hooks.
12. Let the candidate handle representative state-mutating traffic.
13. Stop the candidate and restart fresh base components without silently restoring shared state.
14. Run either raw rollback checks or a disclosed prepared rollback procedure and checks.
15. Return clear mixed-version and rollback verdicts with stable reason codes.
16. Preserve enough evidence to reproduce and diagnose a failure, including exact image IDs and nonsecret environment digests.
17. Provide run-scoped scenario and cross-stage handoff directories for project workloads.
18. Clean up every container, network, worktree, image, volume, and temporary file it owns unless a failed run is explicitly retained.
19. Work locally and in CI without a hosted control plane.
20. Keep setup bounded to one YAML configuration file plus project-owned commands.
21. Support multiple application components when a release includes an API and background processes.
22. Provide a safe demo, a mixed-version failure demo, and a raw rollback failure demo.

---

## 8. Non-goals

Backline must not become:

- a production deployment system;
- a Kubernetes operator;
- a service mesh;
- a database migration linter;
- an OpenAPI diff tool;
- a Postman, Bruno, Hurl, or HTTPie replacement;
- a production traffic recorder;
- a fuzzing framework;
- a load or stress testing tool;
- a chaos engineering platform;
- a general integration test framework;
- a hosted SaaS service;
- a dashboard;
- a user-account system;
- a long-term regression ledger;
- an observability platform;
- an AI analysis product;
- an automatic workload generator;
- a schema inference engine;
- a formal proof system for compatibility;
- a generic remote-environment tester;
- a shared-database or shared-service upgrade orchestrator;
- a sandbox for untrusted pull requests;
- a system for testing component additions or removals that exist in only one revision.

Backline must not automatically:

- reverse migrations;
- restore snapshots;
- reset shared state between scenarios;
- infer whether a workload is representative;
- infer which response or data changes are semantically safe;
- discover, select, or connect to production systems;
- infer that any reachable database, volume, cache, queue, or service is disposable.

Project-supplied hooks and workloads are arbitrary programs. Backline cannot prevent those programs from making external network calls; the user is responsible for keeping them pointed at the isolated verification environment.

---

## 9. Product principles

### 9.1 Test the transition

The system must model the deployment lifecycle, not merely compare outputs from two isolated versions.

### 9.2 Use the same state

Base and candidate components must use the same selected Compose services and the same project-scoped service volumes throughout the run.

### 9.3 Keep the workload explicit

Backline must not pretend to know what application behavior matters. The project supplies commands that represent meaningful operations.

### 9.4 Validate attribution before claiming compatibility failure

Backline must distinguish three things:

- a compatibility failure observed after valid controls;
- a project precondition or candidate correctness failure that prevents a compatibility conclusion;
- an operational failure that prevents Backline from running the required evidence path.

Control workloads reduce false attribution, but they do not prove causality. Backline brackets coexistence with controls on both revisions: base after transition, candidate after startup, and base again while candidate is running.

### 9.5 Continue when safe

A mixed-version failure must not automatically prevent rollback testing. Backline collects both answers whenever the remaining environment is usable. There is no user-facing fail-fast mode.

### 9.6 Leave no hidden infrastructure

Backline must not require its own server, database, daemon, cloud account, or background worker.

### 9.7 Prefer evidence over confidence language

A pass must be worded as no incompatibility detected in configured scenarios, not as a universal safety guarantee.

### 9.8 Protect the developer's machine

All resources must be namespaced by run ID. External Docker resources, host mounts, privileged settings, and remote Docker contexts are rejected by default.

### 9.9 Preserve the original base process through transition

The base-after-transition control must run against the base processes that existed before the candidate transition. Restarting them would erase stale in-process assumptions that the product is intended to test.

### 9.10 Disclose rollback preparation

A rollback hook may alter state. Terminal output and artifacts must therefore identify the result as raw or prepared and must not make the stronger raw-state claim after preparation ran.

### 9.11 Treat project code as trusted

Backline builds and executes both revisions, lifecycle hooks, and workload commands. It is not a security boundary for hostile code and must not be presented as safe for untrusted fork pull requests.

---

## 10. Terminology

| Term | Definition |
| --- | --- |
| **Base revision** | The currently deployed or previous release, usually the target branch or an explicit Git SHA. |
| **Candidate revision** | The release being evaluated, resolved from `--candidate-ref` or `HEAD`. |
| **Candidate configuration** | The committed `backline.yml` read from the resolved candidate revision. |
| **Shared services** | Stateful or supporting services started once and used by both revisions, such as PostgreSQL, Redis, or RabbitMQ. |
| **Component** | A process that belongs to the application release, such as an API or background worker. |
| **Addressable component** | A component with an internal port that Backline publishes on a Docker-assigned random `127.0.0.1` host port for host workloads. |
| **Headless component** | A component such as a worker that has no host URL but participates in the shared environment. |
| **Lifecycle hook** | A project-supplied command used for bootstrap, candidate transition, or rollback preparation. |
| **Workload** | A project-supplied executable command whose completed process exit code determines pass or project-controlled failure. |
| **Scenario** | An ordered sequence of workload steps that share a scenario directory and execute against evolving state. |
| **Baseline** | Base-only evidence collected before candidate transition. |
| **Base-after-transition control** | A base-only control run against the original base processes after candidate transition and before coexistence interpretation. |
| **Candidate-before-coexistence control** | A candidate-targeted control run after candidate startup and before coexistence interpretation. |
| **Base-with-candidate-running control** | A second base control run after candidate control while the original base and candidate processes are both alive. |
| **Mixed-version stage** | The stage where required base and candidate components are alive simultaneously and directional workloads execute. |
| **Candidate-only hook stage** | Optional cutover hooks run after base components stop and before candidate traffic. This may model a backfill, contract migration, or feature activation. |
| **Candidate-traffic stage** | The stage after base components stop and candidate-only hooks complete, where candidate components handle representative state-changing traffic. |
| **Raw rollback** | Fresh base components start directly against the state left by candidate cutover and traffic, with no rollback lifecycle hook. |
| **Prepared rollback** | Project-supplied rollback hooks run before fresh base components start; the verdict covers the complete configured procedure. |
| **Rollback stage** | Candidate components stop, optional rollback preparation runs, and fresh base components are checked against the resulting preserved shared state. |
| **Scenario directory** | A writable temporary directory shared by steps in one scenario only. |
| **Shared handoff directory** | A writable temporary directory shared across hooks and workloads for the duration of one run. It is not copied into artifacts automatically. |
| **Stage outcome** | A pass, project failure, operational error, skip, or incomplete result for one lifecycle stage. |
| **Project-controlled failure** | A configured hook or workload launched successfully and exited nonzero, timed out after successful launch, or an application component failed while its behavior was being checked. |
| **Operational error** | Git, Docker, Compose, process launch, filesystem, shared-environment control, or Backline itself prevented the required check from executing reliably. |
| **Reason code** | A stable machine-readable identifier explaining a stage or verdict result. |
| **Compatibility failure** | A direct base-after-transition, coexistence, or rollback check failed after the required prior controls established a valid evidence path. |
| **Inconclusive** | Backline lacked sufficient valid evidence to make a compatibility determination. |
| **Unsafe Compose feature** | A host bind mount, external resource, privileged container, host namespace, device mapping, Docker socket mount, unsafe config or secret file, or non-loopback published port that requires explicit CLI opt-in. |

---

## 11. User stories

### Required user stories

- As a backend engineer, I can compare `origin/main` with `HEAD` using one command.
- As a platform engineer, I can define shared Compose services once and have both revisions connect to them.
- As a developer, I can reuse existing test scripts rather than rewrite them in a Backline-specific language.
- As a developer, I can verify that the base still behaves correctly after candidate transition while the original base process remains alive.
- As a developer, I can verify that the candidate satisfies a control workload and that the original base still passes after candidate startup before coexistence failures are treated as compatibility evidence.
- As a developer, I can test that data written through the base revision is accepted by the candidate revision.
- As a developer, I can test that data written through the candidate revision is accepted by the base revision.
- As a release owner, I can run candidate-only cutover hooks, let the candidate mutate state, remove it, restart the base revision, and rerun rollback checks.
- As a release owner, I can see whether rollback evidence is raw or includes an explicit preparation procedure.
- As a workload author, I can pass nonsecret record identifiers between stages through a run-scoped handoff directory.
- As a CI system, I can receive stable exit codes, JSON output, JUnit output, and a Markdown summary.
- As a developer, I can see the exact stage, control, scenario, step, revision, component, command, exit code, and log location for a failure.
- As a developer, I can interrupt a run and trust Backline to clean up its resources.
- As a developer, I can retain a failed environment for manual debugging with an explicit flag.
- As a security-conscious user, I can trust Backline not to print configured secret values into reports while understanding that redaction is best effort and project code is trusted.
- As a reviewer, I can run documented demos and observe a safe rollout, a mixed-version failure after controls pass, and a raw rollback failure after candidate traffic.

---

## 12. Command-line interface

The binary name is `backline`.

### 12.1 Required commands

```text
backline verify
backline validate
backline doctor
backline version
backline help
backline --version
backline --help
```

No server command, worker command, history command, dashboard command, or account command is permitted.

### 12.2 `backline verify`

Runs the complete rollout compatibility lifecycle.

```bash
backline verify
```

Supported flags:

```text
--config <path>             Repository-relative path to backline.yml. Default: backline.yml
--base-ref <git-ref>        Override the configured base revision
--candidate-ref <git-ref>   Candidate revision. Default: HEAD
--env-file <path>           Override the host-local Compose environment file
--artifact-dir <path>       Override the artifact root directory
--json-output <path>        Also write the machine-readable summary to this path
--junit-output <path>       Write JUnit XML
--keep-on-failure           Retain run-scoped resources after a non-passing run
--verbose                   Stream full orchestration progress
--no-color                  Disable ANSI output
--allow-unsafe-compose      Permit otherwise-rejected Compose features after an explicit warning
--allow-remote-docker       Permit a non-local Docker context or DOCKER_HOST
```

Behavior:

- CLI flags override matching configuration values.
- `--candidate-ref` is resolved before configuration is loaded. The configuration is then read from that committed candidate revision.
- `--config` is repository-relative and must remain inside the candidate repository tree after symlink resolution.
- `--env-file` is an explicit host-local input and may be absolute. A configured relative environment-file path must remain inside the invoking repository root.
- `--allow-unsafe-compose` and `--allow-remote-docker` cannot be enabled through YAML.
- Backline continues to collect independent evidence after a scenario failure whenever the environment remains usable. There is no fail-fast flag.
- Without `--keep-on-failure`, cleanup is mandatory after pass, failure, inconclusive result, operational error, timeout, or interruption.
- `--keep-on-failure` applies only after run-scoped resources exist and only when the run is not fully passing.
- When `GITHUB_STEP_SUMMARY` exists, Backline appends its Markdown summary automatically.
- The command prints both compatibility verdicts whenever enough execution occurred to classify them.
- If one verdict can be classified and the other cannot, Backline prints the classified verdict and marks the other `INCONCLUSIVE`.
- Terminal and artifact output always disclose rollback mode as `RAW` or `PREPARED` once it is known.

### 12.3 `backline validate`

Validates the committed candidate configuration and referenced paths without building images or starting Docker resources.

```bash
backline validate --config backline.yml --candidate-ref HEAD
```

Validation includes:

- duplicate YAML-key rejection;
- YAML schema and configuration-version validation;
- unknown fields;
- duplicate component, hook, scenario, and step names within their scopes;
- candidate configuration lookup and Git-ref resolution;
- missing or escaping component paths, build contexts, Dockerfiles, Compose files, environment files, and workload working directories;
- invalid target references;
- missing required baseline, all three controls, coexistence, candidate-traffic, or rollback definitions;
- invalid per-scenario role ordering;
- invalid or empty command arrays;
- unresolved required environment substitutions;
- missing selected Compose services;
- unlisted transitive `depends_on` services;
- missing shared-service health checks;
- globally named volumes or networks;
- fixed published host ports, restart policies, or replica settings;
- host-path inputs that escape permitted roots;
- unsafe Compose features;
- fixed `container_name` conflicts;
- invalid readiness definitions;
- invalid timeouts and repeat counts;
- invalid revision configuration;
- invalid stage targets.

`backline validate` may call `docker compose config` to normalize Compose input, but it must not start containers or create Docker resources.

### 12.4 `backline doctor`

Checks runtime prerequisites and explains corrective actions.

```bash
backline doctor --config backline.yml --candidate-ref HEAD
```

It checks:

- Git is installed.
- The current directory is inside a Git repository.
- The candidate ref resolves and contains the configured configuration path.
- The base ref resolves after configuration is loaded.
- Base and candidate resolve to different commits.
- Docker is installed.
- The Docker daemon is reachable.
- The active Docker context is local unless `--allow-remote-docker` is present.
- Docker Compose v2 is available.
- The configuration is valid.
- Build contexts and Dockerfiles exist in both revisions.
- Required host workload executables are discoverable when statically knowable.
- The configured artifact directory is writable.
- The checkout cleanliness rule is satisfied when applicable.
- The selected Compose services normalize with `docker compose config`, define health checks, and avoid global names, fixed host ports, restart policies, and replica settings.
- Unsafe Compose features are absent unless the corresponding CLI override is present.

A failed check identifies the exact missing prerequisite and a direct remediation.

---

## 13. Exit codes

| Exit code | Meaning |
| ---: | --- |
| `0` | Both compatibility verdicts passed and cleanup succeeded. |
| `10` | One or both compatibility verdicts failed. |
| `20` | No compatibility verdict failed, but one or both were inconclusive because required project evidence or a project-controlled precondition did not complete successfully. |
| `21` | Configuration is invalid or a required isolation safeguard blocked the configuration. |
| `22` | A required local prerequisite is unavailable before verification begins. |
| `23` | Backline could not reliably orchestrate the run because of an operational Git, Docker, Compose, process-launch, filesystem, or environment-control error. |
| `24` | Unexpected internal Backline error. |
| `25` | Verification produced no compatibility failure and no higher-priority error, but cleanup failed. |

Classification guidance:

- Exit `20` covers executed project-controlled stages that leave compatibility unproven, such as an invalid baseline, failed candidate transition command, failed candidate control, incomplete candidate-only cutover, or incomplete candidate traffic.
- Exit `23` covers inability to execute or control the required path, such as failure to invoke Docker, inability to attach a network, loss of the local Docker daemon, artifact I/O failure, or a command that could not be launched after validation.
- A Docker build command that launches and returns a project build failure is project evidence and normally leads to `20`; inability to invoke or control the Docker build operation leads to `23`.

Rules:

- Before verification begins, configuration and prerequisite failures use `21` or `22`.
- After run-scoped execution begins, process-exit precedence is `10` > `24` > `23` > `25` > `20` > `0`.
- An observed compatibility failure remains exit `10` even if cleanup or a later operational problem also occurs. The additional problem remains visible in artifacts.
- Detailed verdict states, stage outcomes, reason codes, rollback mode, operational errors, and cleanup results remain available in JSON and Markdown artifacts.
- `backline validate` uses `0` or `21`.
- `backline doctor` uses `0`, `21`, or `22`.

---

## 14. Configuration file

Backline uses one repository-relative file named `backline.yml` by default.

Configuration loading is deterministic:

1. Resolve `--candidate-ref`, defaulting to `HEAD`.
2. Read `--config` from that committed candidate revision using Git.
3. Parse the candidate configuration.
4. Resolve the base ref from `--base-ref`, configuration, or supported CI metadata.
5. Create worktrees only after both commits and the configuration are known.

The active checkout is not used as an implicit source of application or configuration content. Uncommitted files are never silently included.

The Compose file, component build paths, and default workload files are resolved inside each relevant Git worktree. A configured `shared_environment.env_file` is a host-local input resolved from the invoking repository root; `--env-file` may explicitly supply another path. Raw environment-file contents are never copied into artifacts.

### 14.1 Minimal example

```yaml
version: 1

revisions:
  base: origin/main

shared_environment:
  compose_file: backline.compose.yml
  services: [postgres]

release:
  components:
    - name: api
      build:
        context: .
        dockerfile: apps/api/Dockerfile
      run:
        internal_port: 8080
        scheme: http
        environment:
          DATABASE_URL: postgres://postgres:postgres@postgres:5432/app
        readiness:
          type: http
          path: /health

workloads:
  baseline:
    - name: base is healthy
      steps:
        - name: smoke base
          target: base.api
          command: ["python", "scripts/backline/smoke.py"]

  controls:
    base_after_transition:
      reuse_baseline: true
    candidate_before_coexistence:
      reuse_baseline: true
    base_with_candidate_running:
      reuse_baseline: true

  coexistence:
    base_to_candidate:
      - name: candidate reads base data
        steps:
          - name: create through base
            target: base.api
            command: ["python", "scripts/backline/create.py"]
          - name: read through candidate
            target: candidate.api
            command: ["python", "scripts/backline/read.py"]

    candidate_to_base:
      - name: base reads candidate data
        steps:
          - name: create through candidate
            target: candidate.api
            command: ["python", "scripts/backline/create.py"]
          - name: read through base
            target: base.api
            command: ["python", "scripts/backline/read.py"]

  candidate_traffic:
    - name: candidate mutates state
      mutates_state: true
      steps:
        - name: candidate traffic
          target: candidate.api
          command: ["python", "scripts/backline/candidate_traffic.py"]

  rollback:
    reuse_baseline: true
```

Defaults omitted from this example are defined in the configuration reference.

### 14.2 Complete example

```yaml
version: 1

revisions:
  base: origin/main
  require_clean_checkout: true

artifacts:
  directory: .backline/runs
  max_log_bytes_per_command: 1048576

shared_environment:
  compose_file: backline.compose.yml
  services:
    - postgres
    - redis
  env_file: .env.backline
  startup_timeout_seconds: 120
  shutdown_timeout_seconds: 30

security:
  passthrough_environment:
    - CI
  redact_environment:
    - API_TOKEN

release:
  components:
    - name: api
      build:
        context: .
        dockerfile: apps/api/Dockerfile
      run:
        command: ["java", "-jar", "/app/api.jar"]
        internal_port: 8080
        scheme: http
        environment:
          SPRING_PROFILES_ACTIVE: backline
          JDBC_URL: jdbc:postgresql://postgres:5432/app
          REDIS_URL: redis://redis:6379
        base_environment:
          FEATURE_ARCHIVED_STATUS: "false"
        candidate_environment:
          FEATURE_ARCHIVED_STATUS: "true"
        readiness:
          type: http
          path: /actuator/health
          timeout_seconds: 90

    - name: worker
      build:
        context: .
        dockerfile: apps/worker/Dockerfile
      run:
        command: ["java", "-jar", "/app/worker.jar"]
        environment:
          SPRING_PROFILES_ACTIVE: backline
          JDBC_URL: jdbc:postgresql://postgres:5432/app
          REDIS_URL: redis://redis:6379
        base_environment:
          FEATURE_ARCHIVED_STATUS: "false"
        candidate_environment:
          FEATURE_ARCHIVED_STATUS: "true"
        readiness:
          type: docker-health
          timeout_seconds: 90

lifecycle:
  bootstrap:
    - name: initialize base schema
      revision: base
      runner:
        type: component
        component: api
      command: ["/app/bin/migrate"]
      timeout_seconds: 120

  transition:
    - name: apply candidate schema transition
      revision: candidate
      runner:
        type: component
        component: api
      command: ["/app/bin/migrate"]
      timeout_seconds: 120

  candidate_only:
    - name: complete candidate cutover
      revision: candidate
      runner:
        type: component
        component: api
      command: ["/app/bin/post_deploy"]
      timeout_seconds: 120

  rollback: []

workloads:
  defaults:
    cwd_revision: candidate
    timeout_seconds: 60

  baseline:
    - name: base release is initially healthy
      steps:
        - name: run base smoke workflow
          target: base.api
          command: ["python", "scripts/backline/base_smoke.py"]

  controls:
    base_after_transition:
      reuse_baseline: true
    candidate_before_coexistence:
      reuse_baseline: true
    base_with_candidate_running:
      reuse_baseline: true

  coexistence:
    base_to_candidate:
      - name: candidate reads data produced by base
        steps:
          - name: create an order through base
            target: base.api
            command: ["python", "scripts/backline/create_order.py"]
          - name: read the same order through candidate
            target: candidate.api
            command: ["python", "scripts/backline/read_order.py"]

    candidate_to_base:
      - name: base reads data produced by candidate
        steps:
          - name: create a compatible order through candidate
            target: candidate.api
            command: ["python", "scripts/backline/create_compatible_order.py"]
          - name: read the same order through base
            target: base.api
            command: ["python", "scripts/backline/read_order.py"]

    alternating:
      - name: session survives version switching
        steps:
          - name: create session through base
            target: base.api
            command: ["python", "scripts/backline/create_session.py"]
          - name: update session through candidate
            target: candidate.api
            command: ["python", "scripts/backline/update_session.py"]
          - name: read updated session through base
            target: base.api
            command: ["python", "scripts/backline/read_session.py"]

  candidate_traffic:
    - name: candidate handles representative state-changing traffic
      mutates_state: true
      steps:
        - name: create candidate-only state
          target: candidate.api
          command: ["python", "scripts/backline/candidate_traffic.py"]

  rollback:
    reuse_baseline: false
    scenarios:
      - name: base works after candidate traffic
        steps:
          - name: run rollback verification
            target: base.api
            command: ["python", "scripts/backline/rollback_verify.py"]
```

---

## 15. Configuration requirements

### 15.1 Revisions and configuration source

```yaml
revisions:
  base: origin/main
  require_clean_checkout: true
```

Rules:

- The candidate ref is supplied by `--candidate-ref` or defaults to `HEAD`; it is not read from YAML.
- The configuration path is read from the resolved candidate commit before a candidate worktree is created.
- The base ref is explicit in configuration, provided by `--base-ref`, or derived from supported CI metadata. The final build may derive it from GitHub Actions metadata; other CI systems provide the flag.
- Both refs resolve to commits and must not resolve to the same commit.
- `require_clean_checkout` applies only when the candidate is the active checkout's `HEAD`. When enabled, dirty tracked files cause preflight failure because they are not included in the committed candidate.
- Uncommitted and untracked application changes are never silently included.
- Backline records the candidate configuration source commit, config path, SHA-256 digest, base SHA, and candidate SHA in every report.
- Shallow clones are allowed only when both refs and required objects are available. Doctor output explains how to fetch missing history.

### 15.2 Artifacts

```yaml
artifacts:
  directory: .backline/runs
  max_log_bytes_per_command: 1048576
```

Rules:

- Each run receives a unique run directory.
- Backline never deletes or prunes another run's artifact directory automatically.
- The user or CI system owns artifact retention policy.
- Captured build, hook, workload, readiness, and Docker logs are bounded by the configured byte limit per command or log stream.
- Truncation is visible in terminal output and reports.
- The resolved configuration artifact is sanitized and does not contain substituted secret values.
- Artifact retention concerns files only. Docker resources are cleaned unless a non-passing run is explicitly retained.

### 15.3 Shared environment

```yaml
shared_environment:
  compose_file: backline.compose.yml
  services:
    - postgres
    - redis
  env_file: .env.backline
  startup_timeout_seconds: 120
  shutdown_timeout_seconds: 30
```

Rules:

- The Compose file is resolved from the candidate worktree and remains inside that worktree after symlink resolution.
- A configured relative `env_file` is resolved from the invoking repository root and remains inside that root. `--env-file` may explicitly provide another host-local path.
- Only listed services are permitted. Every transitive `depends_on` service is also listed, otherwise validation fails.
- Every selected shared service defines a Compose health check. Backline waits for all selected services to become `healthy`; merely reaching `running` is insufficient.
- Selected shared-service names do not collide with release component names.
- Selected services start once per run under a unique Compose project name.
- Backline discovers every run-scoped network used by the selected services and attaches release components to the necessary networks.
- Project-scoped named volumes and networks used by selected services are permitted.
- Explicit top-level `name` values for volumes or networks are rejected because they bypass Compose project scoping.
- Fixed published host ports are rejected by default. A required host publication uses a Docker-assigned random port bound to loopback.
- Restart policies other than `no`, Compose scaling, and replica counts greater than one are rejected for selected shared services because Backline owns lifecycle and expects one deterministic service instance.
- External volumes, external networks, host bind mounts, privileged containers, host PID/IPC/network modes, device mappings, Docker socket mounts, and non-loopback published ports are rejected by default.
- All path-valued Compose inputs, including shared-service build contexts, Dockerfiles, service `env_file` entries, `extends` files, configs, and secrets, remain inside the candidate worktree or an explicitly permitted host-local root after symlink resolution. External configs and secrets are rejected by default.
- Unsafe Compose features require the explicit `--allow-unsafe-compose` CLI flag. YAML cannot grant that permission.
- If a selected service publishes a host port, it binds to loopback unless unsafe mode is explicitly enabled.
- Shared services remain running through baseline, transition, controls, coexistence, candidate-only cutover, candidate traffic, and rollback.
- Shared services and their volumes are not reset, recreated, or restored between stages.
- The shared-service definition comes from the candidate test harness and remains fixed. Backline does not model a version transition of PostgreSQL, Redis, brokers, or other shared services.
- Backline does not inspect or understand the semantics of selected services. It records the resolved image ID or build image ID for every selected service so the run can be reproduced more accurately.

### 15.4 Release components

A release contains one or more components, and at least one component is addressable.

```yaml
release:
  components:
    - name: api
      build:
        context: .
        dockerfile: apps/api/Dockerfile
      run:
        internal_port: 8080
        scheme: http
        environment:
          JDBC_URL: jdbc:postgresql://postgres:5432/app
        readiness:
          type: http
          path: /actuator/health
          timeout_seconds: 90
```

Rules:

- Component names are unique and valid for environment-variable normalization.
- Build contexts and Dockerfiles are relative to each revision's worktree and remain inside it after symlink resolution.
- Required build paths exist in both revisions.
- Backline builds separate immutable images for base and candidate.
- Images are tagged and labeled with run ID, revision role, component name, and short SHA.
- `run.command` is optional. If omitted, Docker uses the image's configured `ENTRYPOINT` and `CMD`.
- When `run.command` is present, it is a complete process command: Backline uses the first array element as the container entrypoint and the remaining elements as arguments, replacing the image defaults.
- `run.environment` applies to both revisions. Optional `run.base_environment` and `run.candidate_environment` are merged afterward for the corresponding role.
- Merge precedence is common environment, then role-specific environment, then reserved Backline variables. User configuration cannot override reserved `BACKLINE_` variables.
- Every component container joins all required run-scoped shared-service networks and receives a unique run-scoped name and labels.
- Every component receives `BACKLINE_RUN_ID`, `BACKLINE_REVISION_ROLE`, `BACKLINE_REVISION_SHA`, and `BACKLINE_COMPONENT`.
- Addressable components declare one primary `internal_port` and a URI `scheme`, which defaults to `http`.
- The scheme is used only to construct convenience URL variables. Workloads always receive separate host and port variables and may ignore the URL.
- Addressable ports are published using Docker-assigned random ports bound to `127.0.0.1`, never by preselecting a free host port.
- Headless components omit `internal_port` and participate indirectly. Backline does not claim to route a queue item or other work unit to a specific headless instance unless the project workload provides deterministic gating.
- Backline injects context-appropriate component URL variables using stable run-scoped network aliases such as `base-api` and `candidate-api`.
- Every component defines one readiness mode.
- Candidate-only component introduction and base-only component removal are out of scope. The configured component set and build definitions are valid in both revisions.
- One instance per component per revision is required. Multi-replica, load-balancing, quorum, and autoscaling semantics are out of scope.
- Direct application-component bind mounts or named-volume mounts are out of scope. Shared state is reached through selected Compose services in this build.
- Backline monitors required component containers for unexpected exit throughout every stage in which they are expected to remain alive.

Supported readiness modes:

```text
http
tcp
docker-health
command
```

Readiness semantics:

- `http` performs `GET` using the component scheme and loopback mapping and succeeds on HTTP `200-299` unless an explicit expected status is configured. Projects requiring custom TLS behavior use command readiness.
- `tcp` attempts a connection through the component's loopback mapping.
- `docker-health` requires a Docker health check and waits for `healthy`.
- `command` repeatedly executes an argument-array command inside the running container and succeeds on exit code `0`.
- Readiness attempts, last failure, timeout, and container logs are captured.

### 15.5 Lifecycle hooks

Lifecycle hooks model application-controlled bootstrap, transition, and rollback preparation.

Supported groups:

```text
bootstrap
transition
candidate_only
rollback
```

All groups may be empty when application startup performs the relevant work. `candidate_only` runs after base components stop and before candidate traffic. An empty rollback group means raw rollback.

Each hook supports:

```yaml
- name: apply candidate migrations
  revision: candidate
  runner:
    type: component
    component: api
  command: ["/app/bin/migrate"]
  environment:
    MIGRATION_MODE: rollout
  timeout_seconds: 120
```

Supported runners:

#### Host runner

Runs a command from the selected revision worktree.

```yaml
runner:
  type: host
```

#### Component runner

Runs a one-off container from the selected revision's built component image on the shared Docker networks.

```yaml
runner:
  type: component
  component: api
```

Rules:

- Commands are nonempty arrays. Backline does not invoke a shell implicitly.
- For a component runner, the first command element replaces the image entrypoint and the remaining elements are arguments.
- Users may explicitly call `bash`, `sh`, `powershell`, or another shell as the first command element.
- Host working directories remain inside the selected revision worktree after symlink resolution.
- Hook stdout, stderr, exit code, duration, revision, runner, stage outcome, and reason code are recorded.
- `bootstrap` hooks use the base revision and run before base components start.
- `transition` hooks use the candidate revision and run after baseline passes while original base components remain alive.
- Candidate components start only after transition hooks complete.
- `candidate_only` hooks use the candidate revision and run after base components stop, while candidate components and shared services remain active, and before candidate traffic.
- `rollback` hooks run after candidate components stop and before fresh base components start. A rollback hook may explicitly use either revision.
- A bootstrap hook that starts and exits nonzero prevents a valid baseline and makes both verdicts inconclusive.
- A transition hook that starts and exits nonzero is a failed rollout precondition. It makes unfinished compatibility verdicts inconclusive; it is not labeled a cross-version compatibility failure by itself.
- A candidate-only hook that starts and exits nonzero makes the candidate cutover incomplete. Rollback cannot pass, but a directly observed rollback failure may still be collected.
- A rollback hook that starts and exits nonzero after candidate traffic makes rollback compatibility fail because the configured rollback procedure failed.
- A hook timeout after successful launch is classified like a nonzero exit for that hook's stage.
- A hook that cannot be launched is an operational or configuration failure and does not prove incompatibility.
- Backline does not invent, infer, or automatically execute down migrations.
- When the configured rollback hook group is nonempty, rollback mode is `PREPARED` whether the hooks pass, fail, or cannot launch. An empty rollback hook group means `RAW`.

### 15.6 Workloads

Backline workloads are host-executed commands supplied by the project.

A workload scenario contains ordered steps:

```yaml
- name: candidate reads data produced by base
  steps:
    - name: create record
      target: base.api
      command: ["python", "scripts/create.py"]
      working_directory: .
      environment:
        FIXTURE_MODE: deterministic
      timeout_seconds: 60
    - name: read record
      target: candidate.api
      command: ["python", "scripts/read.py"]
```

Rules:

- Workload commands run from the candidate worktree by default.
- A step may override `cwd_revision` with `base` or `candidate`.
- `working_directory` is relative to the selected worktree and remains inside it after symlink resolution.
- A step may define additional environment variables and a timeout.
- A step may target a currently running addressable component using `<revision>.<component>`.
- A step without a target receives URLs for all currently running addressable components.
- Commands are nonempty argument arrays. Backline does not invoke a shell implicitly.
- If the process starts and exits with code `0`, the step passes.
- If the process starts and exits nonzero, or times out after successful launch, the step reports a project-controlled failure.
- If the executable cannot be launched, the step reports an operational or configuration error and does not establish compatibility failure.
- Backline captures bounded stdout and stderr and records process signal, timeout, duration, and reason code.
- Steps in one scenario share a writable scenario directory.
- Every hook and workload also receives one run-scoped shared handoff directory for nonsecret cross-stage coordination.
- Scenario and handoff directories are temporary execution state and are not copied into artifacts automatically.
- Shared services are not reset between scenarios.
- Scenarios execute in declared deterministic order.
- Backline continues independent scenarios after a failure when the environment remains usable.
- Backline does not parse application assertions from stdout.
- Backline does not determine whether a command truly mutated state.
- `mutates_state: true` is a required user declaration for at least one candidate-traffic scenario.
- True parallel workload execution is out of scope.
- A scenario may define `repeat` from `1` to `100`; repeats remain sequential and receive a repeat index.
- Project workload runtimes and dependencies are the project's responsibility, not Backline's runtime dependencies.

### 15.7 Control workloads

All three controls are required. Each control either reuses baseline scenarios or defines explicit scenarios.

```yaml
controls:
  base_after_transition:
    reuse_baseline: true
  candidate_before_coexistence:
    reuse_baseline: true
  base_with_candidate_running:
    reuse_baseline: true
```

Rules:

- `base_after_transition` runs against the original base processes after transition hooks and before candidate startup.
- When `base_after_transition.reuse_baseline` is true, baseline scenarios are rerun unchanged.
- Explicit base-after-transition scenarios target base components only.
- `candidate_before_coexistence` runs after candidate startup while base components remain alive.
- When `candidate_before_coexistence.reuse_baseline` is true, every explicit `base.<component>` target in the baseline is remapped to `candidate.<component>`. Commands, working directories, environment, timeouts, and ordering remain unchanged.
- Explicit candidate-before-coexistence scenarios target candidate components only.
- `base_with_candidate_running` runs after candidate control while the original base and candidate processes remain alive.
- When `base_with_candidate_running.reuse_baseline` is true, baseline scenarios are rerun unchanged against base targets.
- Explicit base-with-candidate-running scenarios target base components only.
- Reusable baseline scripts should consume `BACKLINE_TARGET_URL` rather than hard-coding a base-role URL.
- Reused controls execute against the same evolving shared state. Their effects are not reset. Baseline reuse therefore requires repeatable workloads or unique run-scoped fixture data; otherwise the project defines explicit control scenarios.
- A base-after-transition or base-with-candidate-running control failure is direct mixed-version evidence and makes mixed-version compatibility fail.
- Candidate startup or candidate-control failure prevents clean compatibility attribution. Unless direct mixed failure already exists, unfinished mixed-version and rollback verdicts remain inconclusive and later candidate lifecycle stages do not run.
- Control success reduces false attribution but does not prove that every later failure is caused solely by cross-version interaction.

### 15.8 Security configuration

```yaml
security:
  passthrough_environment:
    - CI
  redact_environment:
    - API_TOKEN
```

Rules:

- `passthrough_environment` is an allowlist of invoking-process variables that project host commands may receive.
- Backline always supplies the minimal operating-system variables required to start a process, including executable-search and temporary-directory variables.
- `redact_environment` lists variable names whose resolved values are registered for redaction.
- Every nonempty value loaded from `shared_environment.env_file` is registered for redaction by default.
- Invoking-process values override environment-file values during `${NAME}` substitution.
- Only `${NAME}` substitution is required. Shell default, command, and arithmetic expansion syntax is not supported.
- Missing required substitutions fail validation.
- Reserved names beginning with `BACKLINE_` cannot be supplied through passthrough or user-defined environment maps.
- The normalized Compose configuration may contain substituted secrets. Backline may inspect it in memory but never persists or prints it without redaction.
- Minimal environment inheritance reduces accidental exposure but does not make host-executed project code safe or sandboxed.

---

## 16. Execution environment variables

Every lifecycle hook and workload process receives:

```text
BACKLINE_RUN_ID
BACKLINE_STAGE
BACKLINE_BASE_SHA
BACKLINE_CANDIDATE_SHA
BACKLINE_CONFIG_SHA256
BACKLINE_SHARED_DIR
BACKLINE_ARTIFACT_DIR
```

Every lifecycle hook additionally receives:

```text
BACKLINE_HOOK
BACKLINE_HOOK_REVISION
```

Every workload step additionally receives:

```text
BACKLINE_SCENARIO
BACKLINE_STEP
BACKLINE_REPEAT_INDEX
BACKLINE_SCENARIO_DIR
```

For every currently running addressable component, Backline injects normalized connection variables:

```text
BACKLINE_BASE_<COMPONENT>_HOST
BACKLINE_BASE_<COMPONENT>_PORT
BACKLINE_BASE_<COMPONENT>_URL
BACKLINE_CANDIDATE_<COMPONENT>_HOST
BACKLINE_CANDIDATE_<COMPONENT>_PORT
BACKLINE_CANDIDATE_<COMPONENT>_URL
```

For a host command, host and port variables contain the loopback mapping. For a one-off component hook, they contain the run-scoped network alias and internal port. URL variables use the configured component scheme. Variables for components that are not currently running are unset rather than left pointing to stale endpoints.

Examples for a host workload:

```text
BACKLINE_BASE_API_HOST=127.0.0.1
BACKLINE_BASE_API_PORT=49152
BACKLINE_BASE_API_URL=http://127.0.0.1:49152
BACKLINE_CANDIDATE_API_HOST=127.0.0.1
BACKLINE_CANDIDATE_API_PORT=49153
BACKLINE_CANDIDATE_API_URL=http://127.0.0.1:49153
```

When a workload step has a target, it also receives:

```text
BACKLINE_TARGET_ROLE
BACKLINE_TARGET_COMPONENT
BACKLINE_TARGET_HOST
BACKLINE_TARGET_PORT
BACKLINE_TARGET_URL
```

Rules:

- Component names are converted to uppercase snake case for environment variables.
- Project-configured values may use supported `${NAME}` substitution.
- Missing required substitutions fail validation.
- Exact substituted nonempty values are registered for output redaction.
- Backline never prints the full environment.
- `BACKLINE_SHARED_DIR` persists across all stages in one run. It is intended for nonsecret identifiers and coordination data, is not uploaded as an artifact automatically, and is deleted during cleanup unless the failed environment is retained.
- `BACKLINE_SCENARIO_DIR` persists only for the steps and repeats of one scenario and is isolated from other scenarios.
- Project commands must not place secrets in either directory unless the user accepts that Backline cannot automatically inspect or redact arbitrary files.

---

## 17. Required workload groups

### 17.1 Baseline

At least one baseline scenario is required.

Purpose:

- Establish that the base revision, initial shared state, and baseline workload are valid before candidate changes are introduced.
- Prevent an existing failure from being misclassified as a rollout regression.

Rules:

- Every explicit target uses a base component.
- If baseline fails, both compatibility verdicts are `INCONCLUSIVE` and candidate stages do not begin.

### 17.2 Base-after-transition control

This control is required through baseline reuse or explicit scenarios.

Purpose:

- Check whether candidate transition activity broke the still-running base before candidate processes are introduced.
- Preserve and test stale in-process assumptions held by the original base processes.

Rules:

- It runs after all transition hooks and before candidate startup.
- It uses the original base processes; Backline does not restart them.
- Every explicit target uses a base component.
- A launched control workload that fails, or a required base component that exits or loses readiness, makes mixed-version compatibility `FAIL`.
- When the environment remains usable, Backline may continue to candidate and rollback evidence collection even though mixed-version compatibility is already failed.

### 17.3 Candidate-before-coexistence control

This control is required through baseline reuse with target remapping or explicit scenarios.

Purpose:

- Establish that the candidate can start and satisfy a known control workload before directional coexistence failures are interpreted as compatibility evidence.

Rules:

- It runs after candidate readiness while base components remain alive.
- Every explicit target uses a candidate component.
- Candidate startup or control failure is a failed candidate precondition, not direct proof of cross-version incompatibility.
- Unless direct mixed-version failure already exists, mixed-version compatibility is `INCONCLUSIVE`.
- Candidate-control failure prevents the required candidate lifecycle from being established. Later coexistence, candidate-only, candidate-traffic, and rollback verification stages are skipped; rollback is `INCONCLUSIVE`.

### 17.4 Base-with-candidate-running control

This control is required through baseline reuse or explicit scenarios.

Purpose:

- Confirm that the original base still satisfies known behavior after candidate startup and candidate control activity.
- Catch incompatibility introduced by candidate startup side effects or control traffic before directional scenarios begin.

Rules:

- It runs after candidate control and before coexistence scenarios.
- The original base and candidate processes remain alive.
- Every explicit target uses a base component.
- A launched control failure, required base exit, or base readiness loss makes mixed-version compatibility `FAIL`.
- If the candidate control did not pass, this control is skipped because candidate preconditions were not established.

### 17.5 Coexistence: base to candidate

At least one `base_to_candidate` scenario is required.

Purpose:

- Exercise state or work produced through the base revision and consumed through the candidate revision.

Validation rules for every scenario:

- At least one step explicitly targets a base component.
- A later step explicitly targets a candidate component.
- Untargeted steps are allowed but do not satisfy the role-order requirement.
- Backline cannot verify the semantic meaning of write and read operations.

### 17.6 Coexistence: candidate to base

At least one `candidate_to_base` scenario is required.

Purpose:

- Exercise state or work produced through the candidate revision and consumed through the still-running base revision.

Validation rules for every scenario:

- At least one step explicitly targets a candidate component.
- A later step explicitly targets a base component.
- Untargeted steps are allowed but do not satisfy the role-order requirement.

This group is critical because forward compatibility of the base revision is commonly missed by normal tests.

### 17.7 Coexistence: alternating

Alternating scenarios are optional.

Purpose:

- Exercise longer deterministic sequences such as base creates, candidate updates, base reads, candidate deletes, and base confirms behavior.

If configured, an alternating scenario contains at least one explicit base target and one explicit candidate target and contributes to the mixed-version verdict.

### 17.8 Candidate-only hooks

Candidate-only hooks are optional lifecycle hooks, not workloads.

Purpose:

- Model cutover work that legitimately occurs only after base processes have stopped, such as a backfill, contract migration, or feature activation.

Rules:

- They run after coexistence and after base components stop, but before candidate traffic.
- They use candidate revision commands while candidate components and shared services remain active.
- Their effects are preserved for candidate traffic and rollback.
- A failed or incomplete candidate-only hook prevents rollback `PASS`; a directly observed rollback failure remains `FAIL`.

### 17.9 Candidate traffic

At least one `candidate_traffic` scenario is required, and at least one declares:

```yaml
mutates_state: true
```

Purpose:

- Simulate the candidate revision receiving representative traffic after base components have been removed and candidate-only cutover hooks have completed.
- Produce the state against which rollback is evaluated.

Rules:

- Every explicit target uses a candidate component.
- Backline records whether at least one declared mutating scenario actually began and whether the full candidate-traffic group completed.
- If candidate-only hooks or candidate traffic fail or are incomplete, rollback may still be attempted when the environment remains usable and state may have changed.
- A rollback pass after incomplete candidate cutover or traffic is `INCONCLUSIVE` because the required precondition was not established.
- A directly observed rollback failure remains `FAIL` even when candidate cutover or traffic was incomplete.

### 17.10 Rollback verification

Rollback defines either:

```yaml
reuse_baseline: true
```

or at least one explicit rollback scenario.

Purpose:

- Verify fresh base components after candidate cutover, candidate traffic, and any configured rollback preparation.

Rules:

- Candidate components stop before rollback preparation or verification.
- Existing base components from coexistence are not reused.
- Fresh base components start from the already-built base images.
- Shared services and service volumes are not restored or recreated.
- Rollback lifecycle hooks run before fresh base components start.
- Every explicit rollback target uses a base component.
- Baseline reuse runs the baseline scenarios unchanged against fresh base targets.
- A fresh base application startup or readiness failure after valid candidate cutover and traffic is a rollback failure when shared services and orchestration remain healthy.
- A launched rollback workload that exits nonzero or times out is a rollback failure.
- Backline reports whether baseline reuse or explicit rollback scenarios produced the verdict and whether rollback mode was raw or prepared.

---

## 18. Execution lifecycle

`backline verify` implements the following deterministic lifecycle.

### 18.1 Preflight

1. Locate the Git repository root.
2. Resolve the candidate ref from `--candidate-ref` or `HEAD`.
3. Read the repository-relative configuration from the resolved candidate commit.
4. Parse configuration with duplicate-key and unknown-field rejection.
5. Resolve the base ref from flag, configuration, or supported CI metadata.
6. Confirm that base and candidate resolve to different full SHAs.
7. Check Git, Docker, Compose, artifact permissions, checkout cleanliness, path confinement, shared-service health checks, and host workload prerequisites.
8. Confirm that Docker is local unless `--allow-remote-docker` is present.
9. Normalize selected Compose services and reject unsafe features unless `--allow-unsafe-compose` is present.
10. Create a run ID, artifact directory, temporary shared handoff directory, redacted resolved configuration, and initial event log.

### 18.2 Isolated workspaces

1. Create one detached Git worktree for the base SHA.
2. Create one detached Git worktree for the candidate SHA.
3. Never modify the user's active checkout.
4. Record worktree paths and commit SHAs in the event log.
5. Remove worktrees during cleanup unless a non-passing run is explicitly retained.

### 18.3 Build

1. Build every configured component from the base worktree.
2. Build every configured component from the candidate worktree.
3. Use immutable run-scoped image tags and labels.
4. Build independent images concurrently when safe, without weakening log attribution or cancellation.
5. Capture bounded build logs to artifacts.
6. Record exact component image IDs.
7. A Docker build that starts and returns a project build failure makes both verdicts inconclusive with reason `BUILD_FAILED`.
8. Inability to invoke or control the build operation is an operational error.

### 18.4 Shared services

1. Start selected shared services once with a unique Compose project name.
2. Wait for every selected service health check to report `healthy`.
3. Discover and record run-scoped networks, volumes, and exact shared-service image IDs.
4. Keep shared services alive for the full verification lifecycle.
5. Never reset shared state between later stages.
6. Failure to establish or retain the shared environment makes unfinished verdicts inconclusive and records an operational error.

### 18.5 Bootstrap and base startup

1. Execute configured base bootstrap hooks.
2. A launched bootstrap hook that exits nonzero prevents a valid baseline and makes both verdicts inconclusive.
3. Start all base components.
4. Wait for every base component readiness check.
5. A base application startup or control failure before baseline makes both verdicts inconclusive.

### 18.6 Baseline

1. Run all baseline scenarios against base components.
2. If any baseline scenario fails, stop candidate stages and classify both verdicts as `INCONCLUSIVE` with reason `BASELINE_FAILED`.
3. Record a successful baseline marker in artifacts. This marker is not a state snapshot.

### 18.7 Candidate transition and first base control

1. Keep the original base components running.
2. Execute candidate transition hooks against the same shared services.
3. If a transition hook starts and fails, record a failed transition stage, classify unfinished verdicts as inconclusive, and stop dependent candidate stages. Do not label the hook failure as cross-version incompatibility by itself.
4. If transition succeeds, recheck required base readiness without restarting any base process.
5. Run the required base-after-transition control.
6. If a required base component exits, loses readiness, or the launched base control fails while shared services remain healthy, mixed-version compatibility is `FAIL`.
7. Continue to candidate and rollback evidence when the environment remains usable, even if mixed-version compatibility is already failed.

### 18.8 Candidate startup and candidate control

1. Start all candidate components while usable base components remain alive.
2. Wait for every candidate component readiness check and continue monitoring base components.
3. Run the candidate-before-coexistence control.
4. Candidate startup or candidate-control failure is a failed precondition. Unless mixed-version compatibility already has direct failure evidence, it remains `INCONCLUSIVE`.
5. If candidate control does not pass, skip the second base control, coexistence, candidate-only hooks, candidate traffic, and rollback verification. Rollback is `INCONCLUSIVE`.

### 18.9 Second base control

1. Keep the original base and candidate components running.
2. Recheck required base readiness.
3. Run the base-with-candidate-running control.
4. If the original base exits, loses readiness, or the launched control fails while shared services and orchestration remain healthy, mixed-version compatibility is `FAIL`.
5. This control brackets directional coexistence scenarios after candidate startup and candidate control activity.

### 18.10 Mixed-version coexistence

1. Enter this stage only after candidate control passes and required components for configured scenarios remain available.
2. Keep base and candidate components running simultaneously.
3. Run every `base_to_candidate` scenario.
4. Run every `candidate_to_base` scenario.
5. Run every configured alternating scenario.
6. Continue across scenario failures when the environment remains usable.
7. Do not reset shared state between scenarios.
8. Monitor required containers throughout the stage.
9. A launched coexistence workload that fails, or a required application component that fails during the stage while orchestration and shared services remain healthy, makes mixed-version compatibility `FAIL`.
10. An operational failure that prevents execution makes an unfinished verdict inconclusive unless direct mixed-version failure evidence was already observed.

### 18.11 Candidate-only cutover

1. Stop all base components.
2. Keep candidate components and all shared services running.
3. Execute configured `candidate_only` hooks in order.
4. Preserve all resulting state, including partial state from a failed hook.
5. A failed or incomplete candidate-only hook prevents rollback `PASS`.
6. If the environment remains usable, proceed directly to rollback diagnostics; do not run representative candidate traffic after an incomplete cutover.

### 18.12 Candidate traffic

1. Run candidate-traffic workloads in declared order after candidate-only hooks complete.
2. Preserve all resulting state, including partial state from a failed candidate workload.
3. Record whether the candidate-traffic group completed and whether a declared mutating scenario began.
4. Incomplete candidate traffic prevents rollback `PASS`; a direct rollback failure may still receive `FAIL`.

### 18.13 Rollback

1. Stop all candidate components.
2. Do not restore shared services or volumes.
3. Determine rollback mode: `RAW` when no rollback hook is configured, otherwise `PREPARED`.
4. Execute configured rollback hooks, if any.
5. A launched rollback hook that fails makes rollback compatibility `FAIL` because the configured rollback procedure failed.
6. Start fresh base components from existing base images.
7. Wait for readiness and monitor required containers.
8. If shared services and orchestration remain healthy but a fresh base component exits or fails readiness after valid candidate cutover and traffic, rollback compatibility is `FAIL`.
9. Run baseline-reuse or explicit rollback scenarios.
10. A launched rollback workload failure makes rollback compatibility `FAIL`.
11. Operational failures make an unfinished rollback verdict inconclusive unless direct rollback failure evidence already exists.

### 18.14 Reporting

1. Finalize both verdicts, all three controls, stage outcomes, rollback mode, and stable reason codes.
2. Write human-readable and machine-readable artifacts, including partial results.
3. Print the two verdicts prominently.
4. State the workload-coverage and attribution limitations.
5. Append the Markdown report to GitHub Step Summary when available.

### 18.15 Cleanup

1. Unless a non-passing run is explicitly retained, stop and remove all component containers.
2. Stop and remove the run-scoped Compose project.
3. Remove every run-scoped network and volume Backline owns.
4. Remove run-scoped images.
5. Remove Git worktrees.
6. Remove temporary scenario and shared handoff directories.
7. Remove temporary files not part of artifacts.
8. Cleanup runs after normal completion, failure, inconclusive result, timeout, SIGINT, SIGTERM, or unexpected error.
9. Cleanup errors are reported separately and do not overwrite compatibility evidence.
10. `--keep-on-failure` retains only resources labeled with the current run ID and prints exact cleanup commands and retained temporary paths.

---

## 19. Verdict model

Each primary verdict has one of three states:

```text
PASS
FAIL
INCONCLUSIVE
```

Every non-pass verdict includes at least one stable reason code and a human-readable explanation. Stage outcomes and operational errors remain separate from the two primary verdicts.

### 19.1 Mixed-version compatibility

#### PASS

All of the following are true:

- a valid base baseline was established;
- candidate transition completed;
- the original base passed the base-after-transition control;
- candidate startup and candidate control passed;
- the original base passed the base-with-candidate-running control;
- all configured coexistence scenarios completed successfully;
- no required application component failed during coexistence.

Required wording:

> No incompatibility detected in the configured mixed-version controls and scenarios.

#### FAIL

After a valid baseline, Backline directly observed at least one of the following while shared services and orchestration remained healthy:

- the original base exited, lost readiness, or failed its control after candidate transition;
- the original base exited, lost readiness, or failed its second control after candidate startup and control;
- a coexistence scenario launched and failed after candidate control passed;
- a required application component failed during coexistence after controls passed.

Required wording:

> A configured mixed-version control or scenario detected incompatible behavior.

The report identifies the exact control, scenario, and reason. Successful controls reduce false attribution but do not prove that version interaction was the sole cause.

#### INCONCLUSIVE

Backline could not establish enough valid evidence and no direct mixed-version failure had already been observed.

Examples:

- base baseline failed;
- image build failed;
- candidate transition command failed;
- candidate failed startup or candidate control;
- shared services or orchestration failed;
- a required command could not be launched;
- the run was interrupted before required evidence completed.

### 19.2 Rollback compatibility

#### PASS

All of the following are true:

- candidate startup and candidate control passed;
- candidate-only hooks, if configured, completed successfully;
- the required candidate-traffic group completed successfully and a declared mutating scenario ran;
- candidate components stopped;
- configured rollback hooks, if any, completed;
- fresh base components started successfully;
- every rollback verification passed.

Required wording for raw rollback:

> The base revision passed the configured rollback checks directly against state left by candidate cutover and traffic.

Required wording for prepared rollback:

> The base revision passed the configured rollback checks after candidate cutover, traffic, and the configured rollback preparation.

#### FAIL

After a valid baseline and candidate state-changing execution, Backline directly observed at least one of the following while shared services and orchestration remained healthy:

- a configured rollback hook launched and failed;
- fresh base components exited or failed readiness;
- a rollback scenario launched and failed;
- a required base component failed during rollback verification.

Required wording for raw rollback:

> The base revision failed against state left by candidate cutover and traffic.

Required wording for prepared rollback:

> The configured rollback procedure failed after candidate cutover and traffic.

#### INCONCLUSIVE

Backline could not establish enough of the candidate and rollback lifecycle to make a compatibility determination and no direct rollback failure had already been observed.

Examples:

- candidate startup or control failed;
- candidate-only cutover failed or did not complete;
- candidate traffic did not complete;
- no declared mutating candidate scenario ran;
- a rollback command could not be launched;
- the shared environment or orchestration became unavailable;
- the run was interrupted.

A directly observed rollback failure takes precedence over earlier uncertainty and remains `FAIL`.

### 19.3 Required reason codes

The final implementation defines and tests at least these stable reason codes:

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

Additional reason codes may be added, but existing meanings do not change without incrementing the machine-readable schema version.

### 19.4 Overall result model

Top-level result status is one of:

```text
PASS
FAIL
INCONCLUSIVE
ERROR
```

- `FAIL` means at least one primary compatibility verdict is `FAIL`.
- `INCONCLUSIVE` means neither verdict failed, but at least one is inconclusive because required project evidence was not established.
- `ERROR` means no compatibility failure took process-exit precedence and Backline encountered an operational, internal, or cleanup error.
- `PASS` means both primary verdicts passed and cleanup succeeded.

Both individual verdicts, all controls, stage outcomes, rollback mode, operational errors, reason codes, image identities, and cleanup state remain separately available.

---

## 20. Human-readable output

Terminal output is concise by default and diagnostic when a failure occurs.

### 20.1 Passing example

```text
Backline verify

Base       origin/main  a81c92f
Candidate  HEAD         f1073bd
Run        bl-20260901-190900-7f3c

Baseline
  PASS  2 scenarios

Controls
  PASS  base after transition
  PASS  candidate before coexistence
  PASS  base with candidate running

Mixed-version compatibility
  PASS  No incompatibility detected in the configured mixed-version controls and scenarios.
  base -> candidate: 2 passed
  candidate -> base: 2 passed
  alternating: 1 passed

Candidate traffic
  PASS  1 state-mutating scenario

Rollback compatibility [RAW]
  PASS  The base revision passed the configured rollback checks directly against state left by candidate cutover and traffic.
  2 scenarios passed

Artifacts
  .backline/runs/bl-20260901-190900-7f3c

Result: PASS
```

### 20.2 Rollback failure example

```text
Backline verify

Base       origin/main  a81c92f
Candidate  HEAD         f1073bd
Run        bl-20260901-190900-7f3c

Controls
  PASS  base after transition
  PASS  candidate before coexistence
  PASS  base with candidate running

Mixed-version compatibility
  PASS  No incompatibility detected in the configured mixed-version controls and scenarios.

Rollback compatibility [RAW]
  FAIL  The base revision failed against state left by candidate cutover and traffic.
  Reason: ROLLBACK_SCENARIO_FAILED

Failed scenario
  Stage:      rollback
  Scenario:   base works after candidate traffic
  Step:       read candidate-created order
  Target:     base.api
  Command:    python scripts/backline/rollback_verify.py
  Exit code:  1

stderr
  GET /orders/184 returned 500
  unknown order status: ARCHIVED

Logs
  .backline/runs/bl-20260901-190900-7f3c/logs/rollback/base-works-after-candidate-traffic.log

Result: FAIL
```

### 20.3 Inconclusive attribution example

```text
Candidate control
  FAIL  candidate smoke workflow exited 1
  Reason: CANDIDATE_CONTROL_FAILED

Mixed-version compatibility
  INCONCLUSIVE  Candidate correctness was not established before coexistence.

Rollback compatibility [RAW]
  INCONCLUSIVE  Required candidate traffic was not established.

Result: INCONCLUSIVE
```

Prepared rollback output replaces `[RAW]` with `[PREPARED]` and states that configured rollback preparation ran.

---

## 21. Artifacts

Every run produces the following when possible, including partial runs:

```text
<artifact-root>/<run-id>/
  report.md
  summary.json
  events.jsonl
  resolved-config.redacted.yml
  logs/
  builds/
```

Optional output:

```text
junit.xml
```

All timestamps use RFC 3339 UTC.

`--artifact-dir` overrides the artifact root. `--json-output` writes an additional copy of `summary.json` to the requested path; the canonical run-local summary remains required.

Backline does not automatically delete older artifact directories.

### 21.1 `report.md`

The report includes:

- Backline version;
- run ID;
- start and finish times;
- candidate configuration path, source SHA, and SHA-256 digest;
- repository-relative environment-file path when it is inside the repository, or only an `[external]` marker and basename otherwise, plus its SHA-256 digest without recording its contents;
- base ref and SHA;
- candidate ref and SHA;
- host platform;
- Docker context, Docker version, and Compose version;
- selected shared services, discovered run-scoped networks, and exact resolved service image IDs;
- release components and exact base and candidate image IDs;
- lifecycle stage and control outcomes;
- mixed-version verdict and reason codes;
- rollback verdict, rollback mode, and reason codes;
- every failed or inconclusive hook, control, scenario, and step;
- operational errors separately from project-controlled failures;
- exact log paths;
- cleanup outcome and remaining resources, if any;
- the workload-coverage and causal-attribution disclaimers.

### 21.2 `summary.json`

The schema is stable and versioned.

Minimum top-level structure:

```json
{
  "schema_version": 1,
  "run_id": "bl-...",
  "status": "FAIL",
  "exit_code": 10,
  "config": {
    "path": "backline.yml",
    "source_sha": "...",
    "sha256": "..."
  },
  "base": {
    "ref": "origin/main",
    "sha": "..."
  },
  "candidate": {
    "ref": "HEAD",
    "sha": "..."
  },
  "environment": {
    "env_file_display": ".env.backline",
    "env_file_external": false,
    "env_file_sha256": "...",
    "shared_service_images": {}
  },
  "component_images": {},
  "rollback_mode": "RAW",
  "verdicts": {
    "mixed_version": {
      "status": "PASS",
      "reason_codes": ["NONE"]
    },
    "rollback": {
      "status": "FAIL",
      "reason_codes": ["ROLLBACK_SCENARIO_FAILED"]
    }
  },
  "controls": {
    "base_after_transition": "PASS",
    "candidate_before_coexistence": "PASS",
    "base_with_candidate_running": "PASS"
  },
  "stages": [],
  "scenarios": [],
  "operational_errors": [],
  "artifacts": {},
  "cleanup": {
    "status": "PASS",
    "reason_codes": ["NONE"]
  }
}
```

The schema is contract-tested. Removing or changing the meaning of an existing field or reason code requires a schema-version increment.

### 21.3 `events.jsonl`

Each line is one redacted JSON object with at least:

```json
{"timestamp":"2026-09-01T19:09:00Z","type":"SCENARIO_STEP_FAILED","stage":"rollback","run_id":"bl-...","details":{}}
```

Required event families include:

```text
RUN_CREATED
CONFIG_RESOLVED
WORKTREE_CREATED
IMAGE_BUILD_STARTED
SHARED_SERVICES_READY
BASE_READY
TRANSITION_STARTED
BASE_CONTROL_COMPLETED
CANDIDATE_READY
CANDIDATE_CONTROL_COMPLETED
BASE_WITH_CANDIDATE_CONTROL_COMPLETED
SCENARIO_STEP_STARTED
SCENARIO_STEP_FAILED
CANDIDATE_STOPPED
ROLLBACK_MODE_SELECTED
BASE_RESTARTED
VERDICT_FINALIZED
CLEANUP_COMPLETED
```

Event ordering reflects emission order. Events never contain raw secret values.

### 21.4 Redacted resolved configuration

`resolved-config.redacted.yml` contains the fully defaulted effective configuration with secret substitutions replaced by `[REDACTED]`. It does not contain raw environment-file contents.

### 21.5 JUnit

When requested:

- each lifecycle hook, control, and workload scenario appears as a JUnit test case;
- a dedicated verdict suite contains one synthetic test case for mixed-version compatibility and one for rollback compatibility;
- failed scenario details identify the failed step;
- compatibility failures are failed test cases;
- operational errors are error test cases;
- skipped dependent work is skipped;
- the XML is valid and accepted by common CI systems.

### 21.6 Temporary execution directories

`BACKLINE_SCENARIO_DIR` and `BACKLINE_SHARED_DIR` are not artifact directories. Backline does not copy their arbitrary contents into artifacts because it cannot safely classify or redact them. They are removed during cleanup unless a non-passing run is explicitly retained.

---

## 22. Secret handling

Backline must:

- never print the full invoking environment;
- use a minimal inherited environment for host commands plus explicitly allowed variables;
- redact exact nonempty values supplied through environment substitution or environment files;
- redact common token, password, authorization, API key, private key, and connection-string patterns;
- apply redaction to terminal output, Markdown, JSON, JUnit, event logs, build logs, hook logs, workload logs, readiness logs, and Docker logs;
- preserve a visible marker such as `[REDACTED]`;
- write only `resolved-config.redacted.yml`, never an unredacted resolved configuration;
- avoid writing the user's environment file or raw normalized Compose interpolation output into artifacts;
- avoid copying arbitrary scenario or shared-handoff files into artifacts;
- never send telemetry or run data to a remote service;
- never require credentials for Backline itself.

Limitations are documented:

- Backline cannot guarantee redaction of arbitrary application-specific secrets printed by project commands.
- Very short, encoded, hashed, split, or transformed secrets may not be detected reliably.
- Backline does not inspect arbitrary files created by project commands for secrets.
- Users remain responsible for preventing hooks and workloads from dumping sensitive data.
- Redaction is an output-control mechanism, not a sandbox or permission boundary.

---

## 23. Safety and trust requirements

Backline operates only on isolated, run-scoped local resources by default.

### 23.1 Required safeguards

- Unique run ID labels on every Docker resource Backline creates.
- Unique Compose project name and run-scoped container names.
- Docker-assigned application ports bound to `127.0.0.1` only.
- Rejection of external volumes, networks, configs, and secrets by default.
- Rejection of explicit global volume and network names that bypass Compose project scoping.
- Rejection of fixed published host ports, restart policies other than `no`, and replica counts greater than one for selected shared services.
- Rejection of host bind mounts by default, including read-only Docker socket mounts.
- Rejection of every path-valued Compose input that escapes permitted roots, including build contexts, Dockerfiles, service environment files, `extends` files, configs, and secrets.
- Rejection of fixed `container_name`.
- Rejection of `privileged`, host PID/IPC/network modes, device mappings, and Docker socket access by default.
- Rejection of shared-service ports published on non-loopback interfaces by default.
- Rejection of build contexts, Dockerfiles, Compose files, configured environment files, and workload working directories that escape permitted roots after symlink resolution.
- Refusal to use a remote Docker context or nonlocal `DOCKER_HOST` without `--allow-remote-docker`.
- Unsafe Compose behavior is enabled only by `--allow-unsafe-compose`; candidate YAML cannot grant itself permission.
- Clear terminal and report warnings when any unsafe override is active.
- No destructive cleanup of resources that lack the current run ID labels.
- `--keep-on-failure` prints exact manual cleanup commands and retained paths.
- Backline never infers that a reachable database or service is disposable.

### 23.2 Trust model

Backline executes Docker builds, application images, lifecycle hooks, and workload commands from selected revisions. Therefore:

- candidate and base revisions are trusted code;
- Backline is not a sandbox;
- host commands can read files and use network access available to the invoking user;
- Docker access commonly gives code broad control over the host and must be treated as privileged;
- the standard CI example never uses `pull_request_target` for candidate code execution;
- the standard CI example skips or separately gates untrusted fork pull requests;
- CI does not expose repository secrets to untrusted candidate code;
- unsafe override flags do not appear in the standard CI workflow;
- dedicated ephemeral CI runners are recommended for public repositories and externally contributed code.

### 23.3 Production boundary

Backline itself does not discover or select remote production resources. The supported path is an ephemeral local Docker environment. Because project-supplied code is arbitrary, Backline cannot guarantee that a malicious or misconfigured hook or workload will not contact an external endpoint. This limitation is explicit in documentation and terminal warnings for unsafe overrides.

---

## 24. Failure handling

### 24.1 Process outcome categories

- A process that launches and exits `0` passes.
- A process that launches and exits nonzero is a project-controlled failure classified according to its lifecycle stage.
- A process that launches and exceeds its timeout is treated as a project-controlled failure for that stage unless Backline lost control of the environment.
- A process that cannot be launched is an operational or configuration error and does not prove incompatibility.
- Backline captures exit code, signal, bounded stdout, bounded stderr, duration, target, working directory, stage outcome, and reason code.
- Backline does not retry automatically. Configured `repeat` values are explicit repeated evidence, not hidden retries.

### 24.2 Stage-specific classification

| Event | Primary classification |
| --- | --- |
| Base build, bootstrap, startup, readiness, or baseline project failure | Both verdicts `INCONCLUSIVE` |
| Candidate transition hook starts and fails | Unfinished verdicts `INCONCLUSIVE`; transition stage `FAIL` |
| Original base loses readiness or fails control after transition | Mixed-version `FAIL` |
| Candidate startup or candidate control fails | Mixed-version and rollback `INCONCLUSIVE` unless a direct mixed failure already exists |
| Base-with-candidate-running control fails | Mixed-version `FAIL` |
| Coexistence workload fails after controls pass | Mixed-version `FAIL` |
| Required application component fails during coexistence after controls pass | Mixed-version `FAIL` |
| Candidate-only hook or candidate traffic fails or is incomplete | Rollback cannot `PASS`; continue to direct rollback diagnostics when safe |
| Rollback hook starts and fails | Rollback `FAIL` |
| Fresh base application startup or readiness fails after valid candidate cutover and traffic | Rollback `FAIL` |
| Rollback workload fails | Rollback `FAIL` |
| Shared-environment control, command launch, filesystem, Docker daemon, or orchestration failure | Affected unfinished verdicts `INCONCLUSIVE` plus operational error |

A direct compatibility `FAIL` is not downgraded by a later operational error.

### 24.3 Readiness and timeout attribution

- Initial base readiness failure prevents a valid baseline and is inconclusive.
- Candidate readiness failure prevents candidate control and is inconclusive unless direct mixed failure already exists.
- Original base readiness loss after a successful transition is mixed-version failure when shared services and orchestration remain healthy.
- Fresh base readiness failure during rollback is rollback failure when valid candidate-only cutover and candidate traffic completed and shared services and orchestration remain healthy.
- If readiness cannot be evaluated because Docker, networking, or shared services failed, the result is operational and unfinished verdicts are inconclusive.
- Backline terminates timed-out child processes and then their process groups when supported, and records whether termination succeeded.

### 24.4 Component failure

- Detect unexpected container exit and readiness loss.
- Capture container inspect data and bounded logs.
- Classify the event using the stage-specific table.
- Attempt remaining independent evidence collection when safe.

### 24.5 Shared-service failure

- Capture Compose status, inspect data, and bounded logs.
- Mark affected unfinished verdicts inconclusive.
- Record an operational error.
- Stop dependent workloads and continue to reporting and cleanup.

### 24.6 Interruption

- Handle SIGINT and SIGTERM.
- Stop launching new work.
- Write a partial report and event log.
- Mark unfinished verdicts inconclusive unless direct failure already exists.
- Run cleanup unless a non-passing run is explicitly retained.

### 24.7 Cleanup failure

- Report exact remaining resources.
- Print manual cleanup commands.
- Never claim cleanup succeeded without verifying resource removal.
- Use exit code `25` only when no compatibility failure, internal error, or operational error has higher precedence.

---

## 25. Architecture

Backline uses a single-process, single-binary architecture.

```text
Git refs and candidate configuration
              |
              v
      Isolated worktrees
              |
              v
  Base and candidate image builds
              |
              v
   One shared Compose environment
              |
              v
  Base baseline -> candidate transition
              |
              v
  Base control -> candidate control
              |
              v
  Base-with-candidate control
              |
              v
     Mixed-version scenarios
              |
              v
 Candidate-only hooks -> traffic
              |
              v
 Raw or prepared rollback checks
              |
              v
 Verdicts, reason codes, and artifacts
```

### 25.1 Required implementation characteristics

- Implementation language: Go.
- Deliverable: native `backline` binary.
- No embedded web server.
- No internal database.
- No daemon.
- No plugin process.
- No runtime dependency on a language interpreter for Backline itself.
- Candidate configuration is read from the committed candidate ref before worktrees are created.
- Docker, Compose, Git, filesystem, and process interactions occur through well-tested command wrappers.
- Commands use argument arrays, not shell-concatenated strings.
- Path validation resolves symlinks and enforces repository, worktree, environment-file, and artifact boundaries.
- Addressable ports are discovered from Docker-assigned loopback mappings rather than race-prone preselected ports.
- All external process calls support context cancellation and timeouts.
- The orchestration state machine records stage outcomes separately from primary verdicts.
- Internal modules separate:
  - configuration;
  - Git worktrees;
  - Docker builds;
  - Compose environment management;
  - component lifecycle and readiness;
  - lifecycle hooks;
  - workload and control execution;
  - orchestration state machine;
  - verdict classification and reason codes;
  - artifact rendering;
  - redaction;
  - cleanup.

### 25.2 Suggested repository layout

```text
cmd/backline/
internal/config/
internal/gitops/
internal/dockerops/
internal/compose/
internal/components/
internal/hooks/
internal/workloads/
internal/controls/
internal/orchestrator/
internal/verdicts/
internal/artifacts/
internal/redact/
internal/cleanup/
examples/rollout-demo/
docs/
site/
```

The exact package layout may change, but responsibilities remain isolated and testable.

---

## 26. Distribution

The final build must provide:

- Linux x86-64 binary;
- Linux ARM64 binary;
- macOS x86-64 binary;
- macOS ARM64 binary;
- Windows x86-64 binary;
- SHA-256 checksums;
- versioned GitHub Release artifacts;
- `go install` instructions;
- a GitHub Actions example that downloads a pinned release;
- a Docker image only if it does not complicate Docker-outside-Docker usage.

The primary installation path must not require cloning the repository or compiling from source.

---

## 27. CI integration

Backline is CI-agnostic at runtime and safe by default in documented workflows.

Required CI behavior:

- noninteractive execution;
- stable exit codes;
- JSON output;
- JUnit output;
- no ANSI output when requested;
- partial artifacts on failure;
- automatic GitHub Step Summary support;
- no requirement for a personal access token;
- compatibility with standard Docker-enabled Linux runners;
- no unsafe Compose or remote-Docker override in the standard workflow.

A documented GitHub Actions example:

1. uses `pull_request`, a trusted branch workflow, or manual dispatch, never `pull_request_target` to execute candidate code;
2. checks out full Git history;
3. makes the base ref available;
4. installs a pinned Backline release and verifies its checksum;
5. runs `backline doctor`;
6. runs `backline verify`;
7. uploads the artifact directory even when verification fails;
8. publishes JUnit results when supported;
9. does not expose repository secrets to fork pull requests;
10. skips untrusted fork execution or routes it to a separately isolated, secret-free approval workflow;
11. explains that Docker-enabled execution is privileged and should use ephemeral runners where practical.

Backline does not post pull-request comments through a custom GitHub API client in the core build.

---

## 28. Demo and examples

The repository includes a deterministic demo under:

```text
examples/rollout-demo/
```

The demo creates or uses an isolated temporary Git repository containing base and candidate commits.

It includes three runnable cases.

### 28.1 Safe rollout

Expected result:

```text
Mixed-version compatibility: PASS
Rollback compatibility [RAW]: PASS
```

Required evidence:

- baseline passes;
- all three controls pass;
- both directional coexistence groups pass;
- candidate traffic completes and mutates state;
- fresh base passes raw rollback checks.

### 28.2 Mixed-version failure

Expected result:

```text
Mixed-version compatibility: FAIL
```

The fixture demonstrates a failure that does not appear before the transition or in either control:

- base baseline passes;
- candidate transition completes;
- original base passes the post-transition control;
- candidate starts and passes its control;
- the original base passes again while candidate is running;
- both revisions are alive;
- a candidate-to-base scenario writes a representation through the candidate and the still-running base fails when consuming it.

The end-to-end test asserts that the failing reason is `COEXISTENCE_SCENARIO_FAILED` or `REQUIRED_COMPONENT_EXITED`, not a baseline, transition, or candidate-control failure.

### 28.3 Raw rollback failure

Expected result:

```text
Mixed-version compatibility: PASS
Rollback compatibility [RAW]: FAIL
```

The fixture demonstrates:

- baseline and all three controls pass;
- coexistence uses mutually compatible state and passes;
- candidate-only hooks, if configured, complete;
- candidate traffic writes a new valid candidate representation;
- candidate stops;
- no rollback hook runs;
- fresh base starts or attempts to start against unchanged candidate-written state;
- rollback verification fails because base cannot interpret that representation.

Suggested fixture:

- candidate traffic writes a persisted enum or serialized value such as `ARCHIVED`;
- base understands only prior values;
- candidate functions correctly;
- base fails when reading the candidate-created record after rollback.

### 28.4 Demo requirements

- One command per case.
- No cloud credentials.
- No external services.
- Total documented setup under ten minutes on a machine with Git and Docker already installed.
- Expected output is included in documentation.
- Demo scripts verify observed verdicts, all three controls, rollback mode, reason codes, exact stage attribution, and cleanup automatically.

---

## 29. Testing requirements

### 29.1 Unit tests

Required unit coverage:

- candidate-ref resolution and configuration loading from the candidate commit;
- duplicate YAML-key and unknown-field rejection;
- configuration defaults and schema validation;
- environment substitution and redaction registration;
- path and symlink confinement;
- revision resolution;
- target parsing and per-scenario directional-order validation;
- baseline target remapping for candidate controls and repeated-base controls;
- raw versus prepared rollback-mode selection and wording;
- state-machine transitions;
- stage-specific project, compatibility, and operational classification;
- verdict and reason-code precedence;
- exit-code mapping;
- command construction without implicit shells;
- timeout and process-group termination behavior;
- log truncation;
- secret redaction;
- JSON artifact schema;
- JSONL event schema;
- JUnit rendering, including synthetic verdict cases;
- Docker resource-label filtering;
- Compose safety-rule detection, including configs and secrets;
- shared-service health-check, global-name, fixed-port, restart-policy, and replica validation;
- loopback port mapping parsing and scheme-aware host, port, and URL generation;
- shared and scenario directory lifecycle.

### 29.2 Integration tests

Required integration coverage:

- temporary Git repository with two revisions and committed candidate configuration;
- configuration read from a non-HEAD candidate ref;
- dirty-checkout rule;
- worktree creation and removal;
- base and candidate Docker builds;
- shared Compose startup with multiple run-scoped networks and required health checks;
- base-only baseline;
- candidate transition while original base remains alive;
- base-after-transition control using the original base process;
- candidate startup and candidate control;
- base-with-candidate-running control;
- candidate-control failure resulting in inconclusive compatibility rather than false mixed-version failure;
- transition-hook failure resulting in inconclusive compatibility;
- base-after-transition failure resulting in mixed-version failure;
- simultaneous base and candidate components;
- both directional scenario-order checks;
- coexistence failure classification after all controls pass;
- candidate-only cutover hook success and failure;
- candidate traffic and cross-stage handoff through `BACKLINE_SHARED_DIR`;
- role-specific component environment merge behavior;
- release and component-runner entrypoint replacement semantics;
- raw rollback success and failure;
- prepared rollback success and rollback-hook failure;
- fresh base restart and rollback verification;
- command-launch and operational-error classification;
- SIGINT cleanup;
- retained non-passing environment;
- external-resource rejection;
- bind-mount, config-file escape, secret-file escape, Docker-socket, privileged, host-namespace, and device rejection;
- non-loopback port rejection;
- remote Docker-context rejection through a mocked command boundary;
- headless component participation and documented routing limitation;
- component crash diagnostics;
- partial report generation;
- redacted resolved configuration;
- confirmation that temporary handoff files are not copied into artifacts;
- structured events.

### 29.3 End-to-end fixtures

The safe, mixed-failure, and raw-rollback-failure demos are mandatory end-to-end tests.

The suite asserts:

- exact individual verdicts;
- all three control outcomes;
- rollback mode;
- stable reason codes;
- process exit code;
- expected failed stage;
- expected artifact files and schemas;
- no secret leakage;
- loopback-only published application ports;
- no leaked run-scoped Docker resources after cleanup;
- no leaked Git worktrees.

### 29.4 Platform tests

- Unit tests run on Linux, macOS, and Windows.
- Docker end-to-end tests run on Linux CI.
- Windows Docker Desktop usage is manually documented and smoke-tested.
- Commands and paths do not assume POSIX-only separators.
- Workload commands use argument arrays to remain cross-platform.

### 29.5 Quality gates

The repository enforces:

- formatting;
- static analysis;
- unit tests;
- integration tests;
- end-to-end demo tests;
- validation that both configuration examples in this PRD parse against the implemented schema;
- JSON schema validation;
- JSONL event validation;
- JUnit XML validation;
- release build verification;
- checksum generation;
- secret scanning;
- dependency vulnerability scanning.

No required core-path test is silently skipped in CI because Docker is unavailable. CI provides Docker or fails clearly.

---

## 30. Performance and resource requirements

Backline is not a performance-testing tool. Correct attribution, isolation, cleanup, and reproducible evidence take precedence over an arbitrary wall-clock target.

Required behavior:

- Backline records and reports duration for every stage, hook, control, scenario, step, build, readiness check, and cleanup operation.
- Independent base and candidate image builds may run concurrently when cancellation, log attribution, and deterministic reporting remain correct.
- Workload scenarios execute sequentially by design.
- Default per-step timeout: 60 seconds.
- Default component startup timeout: 90 seconds.
- Default shared-environment startup timeout: 120 seconds.
- Default graceful shutdown timeout: 30 seconds.
- Default maximum captured log size: 1 MiB per command or log stream.
- All limits are configurable within documented bounds.
- Backline prints the currently active operation so users can identify where time is spent.
- Backline creates no daemon or persistent background service.
- Documentation explains how Docker layer caching can reduce repeated build time without weakening isolation.
- Performance optimization must not skip controls, reuse application processes across lifecycle points where freshness is required, reset shared state, or weaken cleanup verification.

---

## 31. Documentation requirements

The repository contains:

```text
README.md
docs/config-reference.md
docs/workload-authoring.md
docs/control-workloads.md
docs/lifecycle-model.md
docs/rollback-modes.md
docs/verdicts-and-reason-codes.md
docs/ci-integration.md
docs/ci-security.md
docs/safety-and-trust.md
docs/troubleshooting.md
docs/known-limitations.md
docs/architecture.md
examples/rollout-demo/README.md
```

### README requirements

The README begins with:

> Backline tests the part of a deployment your normal test suite misses: the base and candidate revisions running against the same state, including whether the base revision still works after rollback.

It shows:

- the two primary questions;
- one command;
- the minimal configuration before the complete reference;
- the baseline and three required attribution controls;
- a passing output example;
- a raw rollback failure example;
- the difference between raw and prepared rollback;
- candidate-only cutover hooks and role-specific component environments;
- setup requirements;
- quick start;
- links to the three demos;
- exact verdict, stage-outcome, reason-code, and exit-code semantics;
- how to use `BACKLINE_SHARED_DIR` for nonsecret cross-stage identifiers;
- the trusted-code, Docker privilege, and CI fork security boundaries;
- explicit limitations;
- no implementation-first architecture pitch above the quick start.

### Public site requirements

The existing static site may be replaced, but the final repository includes a small static product site.

The site:

- leads with the two questions Backline answers;
- shows real CLI output from the demos;
- explains baseline, transition, all controls, coexistence, candidate-only cutover, candidate traffic, and rollback visually;
- links to installation, configuration, demos, GitHub, safety, and limitations;
- states that a pass is bounded by configured workload coverage;
- distinguishes raw from prepared rollback;
- states that Backline executes trusted project code and is not a sandbox;
- avoids presenting a dashboard;
- avoids emphasizing internal implementation details as the main value proposition;
- avoids unsupported claims such as automatic safety proof.

The site is documentation and product communication, not a separate application.

---

## 32. Observability and diagnostics

Backline makes orchestration and project failures diagnosable without `--verbose`.

For every failed command, control, or component, report:

- stage;
- control, scenario, and step when applicable;
- revision role;
- commit SHA;
- component;
- command;
- working directory;
- start time;
- duration;
- exit code or signal;
- timeout state;
- project-controlled versus operational classification;
- stable reason code;
- bounded stdout;
- bounded stderr;
- full log file path;
- related container name;
- related Docker logs path.

Every rollback result reports `RAW` or `PREPARED` and lists executed rollback hooks.

`--verbose` additionally streams:

- Git commands;
- Docker build progress;
- Compose commands;
- container state changes;
- readiness attempts;
- control and scenario transitions;
- cleanup actions.

Verbose output still redacts registered secrets.

---

## 33. Known limitations

The final product documents these limitations prominently:

1. **Workload quality controls coverage.** Backline cannot detect behavior that configured controls and scenarios never exercise.
2. **A pass is not proof.** It means no incompatibility was detected in the configured sequences.
3. **Controls reduce false attribution but do not prove causality.** The three controls bracket the mixed stage, but a later failure may still have multiple causes.
4. **Control reuse requires repeatable workloads.** Reused baseline controls execute again against evolving state and may need unique fixtures.
5. **Shared state evolves across scenarios.** Backline does not reset state between scenarios, so declared order can affect later results.
6. **Execution is sequential.** Backline does not reproduce true concurrent races.
7. **One instance per component per revision.** It does not model fleet size, load balancing, quorum behavior, or autoscaling.
8. **Headless consumer selection may be nondeterministic.** Backline cannot generically force a specific worker to claim a queue item; projects need deterministic gating when that distinction matters.
9. **At least one addressable component is required.** Worker-only systems are not a first-class target in this build.
10. **One primary addressable port per component.** Additional ports are not exposed through first-class Backline variables in this build.
11. **Custom TLS readiness is project-owned.** Components needing custom certificates or TLS verification use command readiness and project workloads.
12. **Docker Compose only.** Kubernetes-native orchestration is out of scope.
13. **No automatic production-state cloning.** The user supplies realistic seed and workload behavior.
14. **No semantic shared-service inspection.** Backline treats databases, caches, brokers, and other services as opaque shared infrastructure.
15. **Shared-service upgrades are not modeled.** The candidate harness defines one fixed shared-service environment for the run.
16. **No automatic migration safety analysis.** Backline executes configured hooks and observes behavior.
17. **Prepared rollback is a different claim from raw rollback.** A preparation hook may transform state before base verification.
18. **No candidate-only component changes.** The configured component set and build paths exist in both revisions.
19. **No direct application filesystem-volume compatibility.** Release components access shared state through selected Compose services in this build.
20. **No external environment verification by default.** The supported safe path is a local ephemeral Compose environment.
21. **No guarantee of deterministic application behavior.** Nondeterministic workloads must be stabilized by the project.
22. **Host workload dependencies are project-owned.** A workload may require Python, Node.js, Java, or another tool even though the Backline binary does not.
23. **No automatic secret classification.** Redaction is best effort, and arbitrary handoff files are not inspected.
24. **Trusted code only.** Backline builds and executes project code and is not safe as a sandbox for hostile revisions.
25. **Project commands can make network calls.** Backline cannot guarantee that a malicious or misconfigured hook will not contact an external service.
26. **No production deployment action.** Backline verifies; it does not deploy.

---
## 34. Risks and mitigations

| Risk | Impact | Required mitigation |
| --- | --- | --- |
| Weak workloads produce false confidence | High | Prominent coverage disclaimer, all three required controls, required bidirectional scenarios, required candidate mutation workload, and workload-authoring guidance. |
| Candidate correctness failure is misreported as mixed-version incompatibility | High | Candidate-before-coexistence control; candidate failure yields inconclusive compatibility unless direct mixed evidence already exists. |
| Candidate transition failure is misreported as version interaction | High | Transition is a separate stage outcome; failed transition makes unfinished verdicts inconclusive. |
| Transition or candidate startup silently breaks an already-running base process | Critical | Preserve the original base process and require one base control after transition plus another after candidate startup and control. |
| Prepared rollback is marketed as untouched-state rollback | High | Mandatory `RAW` or `PREPARED` label and mode-specific fixed wording in every output. |
| Setup becomes framework-specific | High | Docker image and command boundaries only; no ORM-specific or framework-specific engine. |
| Product becomes another test framework | High | Commands remain the assertion interface; no custom HTTP DSL. |
| Candidate-controlled configuration weakens safety | High | Unsafe behavior can be enabled only through explicit CLI flags, never YAML. |
| Untrusted fork code gains Docker or secret access | Critical | Trusted-code boundary, no `pull_request_target`, no secrets for fork runs, explicit fork gating, and dedicated CI security documentation. |
| Compose files touch real or host resources | Critical | Reject external resources, bind mounts, escaping config/secret files, Docker sockets, privileged settings, host namespaces, devices, and remote Docker by default. |
| Services are exposed beyond the machine | High | Bind application ports to `127.0.0.1`; reject non-loopback shared-service publications by default. |
| Candidate transition or candidate-only cutover destroys rollback state | High | Use ephemeral services only, never restore silently, retain diagnostic evidence, and never infer remote state is disposable. |
| Cross-stage workloads cannot find candidate-created records | Medium | Provide a run-scoped nonartifact handoff directory and stable environment variables. |
| Handoff files leak secrets through artifacts | High | Do not copy arbitrary scenario or handoff files into artifacts; document that these directories are not secret stores. |
| Headless workers make queue tests nondeterministic | Medium | Document limitation and require project-controlled gating for deterministic worker handoff scenarios. |
| Nondeterministic workloads create noise | Medium | Deterministic ordering, bounded explicit repeats, clear timeout evidence, and no hidden retry masking. |
| Failed runs leak Docker resources | High | Centralized cleanup manager, run labels, signal handling, cleanup integration tests, and manual cleanup commands. |
| Cross-platform command quoting breaks | Medium | Argument arrays, no implicit shell, path tests, and platform matrix. |
| Build time makes CI expensive | Medium | Concurrent independent builds, Docker cache guidance, stage timing, and no extra Backline services. |
| Scope expands into databases, queues, and Kubernetes | High | Compose services remain opaque; specialized engines and Kubernetes remain explicit non-goals. |
| Marketing overclaims safety | High | Fixed pass wording, controls, reason codes, rollback modes, and documented limitations. |

---

## 35. Acceptance criteria

The build is complete only when every criterion below is true.

### 35.1 Product behavior

- `backline verify` executes baseline, transition, all three controls, coexistence, candidate-only hooks, candidate traffic, and rollback in the documented order.
- The candidate configuration is read from the committed candidate ref before worktrees are created.
- Base and candidate components share one unchanged set of selected services and service volumes.
- The original base remains alive during candidate transition and its post-transition control.
- Candidate startup and candidate control occur before the original base is checked again and before coexistence interpretation.
- Base stops before candidate-only cutover hooks and candidate traffic.
- Candidate stops before rollback.
- Rollback starts fresh base components.
- Shared state is not silently restored before rollback verification.
- Rollback mode is `RAW` when no rollback hook runs and `PREPARED` when one or more rollback hooks run.
- Mixed-version compatibility returns `PASS`, `FAIL`, or `INCONCLUSIVE` with reason codes.
- Rollback compatibility returns `PASS`, `FAIL`, or `INCONCLUSIVE` with mode-specific wording and reason codes.
- Both verdicts appear in terminal, Markdown, JSON, and JUnit outputs.
- Verdict wording does not claim universal safety or unsupported causality.
- Backline has no fail-fast mode and attempts both verdicts whenever the environment remains usable.

### 35.2 Required controls, scenarios, and classification

- Configuration requires baseline scenarios.
- Configuration requires base-after-transition control through reuse or explicit scenarios.
- Configuration requires candidate-before-coexistence control through reuse or explicit scenarios.
- Configuration requires base-with-candidate-running control through reuse or explicit scenarios.
- Configuration requires at least one addressable component.
- Every base-to-candidate scenario contains an explicit base target followed later by an explicit candidate target.
- Every candidate-to-base scenario contains an explicit candidate target followed later by an explicit base target.
- Configuration requires candidate traffic with at least one declared state mutation.
- Configuration requires rollback verification or explicit baseline reuse.
- Baseline, build, bootstrap, transition, candidate startup, or candidate-control failure does not become a compatibility `FAIL` without direct compatibility evidence.
- Base-after-transition or base-with-candidate-running control failure becomes mixed-version `FAIL`.
- Coexistence failure after controls pass becomes mixed-version `FAIL`.
- Incomplete candidate-only cutover or candidate traffic prevents rollback `PASS` but does not hide a directly observed rollback `FAIL`.
- Rollback-hook, fresh-base, or rollback-scenario failure becomes rollback `FAIL` under the documented healthy-environment preconditions.
- The safe demo returns both verdicts as `PASS` and rollback mode `RAW`.
- The mixed-failure demo passes all three controls and returns mixed-version `FAIL` with the expected reason code.
- The rollback-failure demo returns mixed-version `PASS`, rollback `FAIL`, and mode `RAW` with the expected reason code.
- An integration fixture demonstrates prepared rollback and mode-specific wording.

### 35.3 Isolation, trust, and safety

- Every Docker resource Backline creates is run-scoped and labeled.
- Addressable application ports bind only to `127.0.0.1` by default.
- Every selected shared service defines and passes a health check.
- Selected shared services do not use global volume or network names, fixed host ports, restart policies, or multi-replica settings by default.
- External resources, host bind mounts, escaping config or secret files, Docker sockets, privileged settings, host namespaces, devices, and non-loopback publications are rejected by default.
- Unsafe Compose behavior cannot be enabled in YAML.
- Remote Docker is rejected without an explicit CLI flag.
- Candidate-controlled paths cannot escape permitted roots after symlink resolution.
- A normal or interrupted run leaves no Backline Docker resources behind.
- Backline never modifies the active Git checkout.
- Worktrees and temporary scenario or handoff directories are removed after cleanup.
- `--keep-on-failure` retains only current-run resources and prints cleanup instructions.
- Documentation states that trusted revisions are required, Docker access is privileged, and the standard CI example avoids `pull_request_target` and untrusted fork secrets.

### 35.4 Diagnostics and artifacts

- Failed steps identify stage, control or scenario, step, target, command, exit code, classification, reason code, and log path.
- Unexpected component exits include inspect data and bounded container logs.
- Partial reports are written after interruption and operational failure.
- `summary.json` and `events.jsonl` validate against documented schemas.
- `summary.json` includes all three controls, rollback mode, exact image identities, privacy-preserving environment-file display and digest metadata, operational errors, exit code, and cleanup state.
- `resolved-config.redacted.yml` contains defaults but no raw secret substitutions.
- Reports record exact base, candidate, and shared-service image IDs plus the environment-file digest when used, without exposing environment-file contents.
- Secret fixture values do not appear in terminal or artifacts.
- Arbitrary scenario and handoff files are not copied into artifacts automatically.
- Log truncation is explicit.
- Cleanup state and remaining resources are recorded separately from compatibility verdicts.
- Backline does not automatically prune prior artifact directories.

### 35.5 Developer experience

- Installation uses a prebuilt binary.
- `backline doctor` gives actionable prerequisite failures.
- `backline validate` catches invalid configuration, missing control definitions, invalid role ordering, missing shared-service health checks, and unsafe Compose definitions without starting Docker resources.
- A minimal configuration is documented before the complete reference.
- Both YAML examples in this PRD parse and validate.
- The documented demo is runnable in under ten minutes after prerequisites are installed.
- The core quick start does not require a Backline server, Backline database, account, Java, Node.js, or Python for the Backline binary itself.
- The configuration can target an addressable component through host, port, and scheme-aware URL variables and can include optional headless components.
- Workloads can be ordinary project scripts in any language available to the project.
- `BACKLINE_SHARED_DIR` allows nonsecret cross-stage handoff and is cleaned or retained according to run policy.
- Component and hook command arrays have tested entrypoint-replacement semantics.

### 35.6 CI and quality

- Linux CI runs the full Docker end-to-end suite.
- Cross-platform unit tests pass.
- JSON, JSONL, and JUnit outputs validate.
- JUnit includes synthetic test cases for both primary verdicts.
- Release binaries and checksums are generated automatically.
- Required tests are not skipped silently.
- Static analysis and security scans pass.
- Documentation reflects actual CLI behavior, candidate-only cutover hooks, rollback modes, all attribution controls, role-specific environments, and security boundaries.
- No placeholder logic remains in the core lifecycle.
- No TODO item is accepted in a required execution path.

---

## 36. Repository transition

Before replacing the active implementation:

1. Tag the current repository state with a clear legacy tag.
2. Preserve the old implementation through Git history.
3. Replace the active architecture with the single-binary CLI.
4. Remove the old Backline API server, Backline worker, Backline-owned PostgreSQL result store, Flyway migrations for Backline's own data, and regression-ledger commands from the active product tree.
5. Do not keep obsolete code in `legacy/` inside the active repository merely to avoid deletion.
6. Rewrite the README and site only after the new end-to-end demos pass.
7. Keep the MIT license unless deliberately changed.

---

## 37. Definition of done

Backline is done when a developer can point it at two trusted Git revisions of a Dockerized stateful service, provide one healthy Compose environment and a small set of executable baseline, control, directional, candidate-traffic, and rollback workloads, run:

```bash
backline verify
```

and receive reproducible, evidence-backed answers to:

```text
Mixed-version compatibility: PASS | FAIL | INCONCLUSIVE
Rollback compatibility:      PASS | FAIL | INCONCLUSIVE
Rollback mode:               RAW | PREPARED
```

A mixed-version pass requires a valid baseline, a passing original-base control after transition, a passing candidate control, a second passing original-base control while candidate is running, and passing directional coexistence scenarios. A rollback pass requires successful candidate-only cutover hooks when configured, successful candidate traffic, fresh base processes, and passing raw or prepared rollback checks.

Each non-pass answer includes stable reason codes and diagnostic artifacts. Each pass is explicitly bounded by configured workload coverage and does not claim universal safety or sole causality.

The final product demonstrates one safe rollout, one failure observed during mixed-version operation only after controls pass, and one raw rollback failure observed after candidate-written state is handed back to fresh base processes.

No additional server, Backline-owned database, account, dashboard, language runtime for Backline itself, or hosted service is required to obtain those answers.
