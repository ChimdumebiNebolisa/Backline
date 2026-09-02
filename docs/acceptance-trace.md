# Acceptance trace

This trace maps every criterion in PRD section 35 to executable evidence or a committed documentation artifact. Number suffixes follow each bullet's order within its subsection.

| PRD criteria | Evidence |
| --- | --- |
| 35.1-01–09 lifecycle ordering and shared state | `internal/orchestrator/state_test.go`, `internal/orchestrator/lifecycle.go`, and all rollout-demo harness cases |
| 35.1-10 rollback mode | `internal/verdicts/classifier_test.go`; safe and prepared-rollback demos |
| 35.1-11–12 primary verdicts, reasons, and wording | `internal/verdicts/classifier_test.go`, `internal/reporting/reporting_test.go`, and `docs/verdicts-and-reason-codes.md` |
| 35.1-13 both verdicts in all outputs | `internal/reporting/reporting_test.go` and `internal/contracts/schema_test.go` |
| 35.1-14 bounded claims | reporting golden assertions plus `docs/safety-and-trust.md` |
| 35.1-15 continue when usable | `internal/orchestrator/state_test.go` and candidate-only-failure demo |
| 35.2-01–05 required scenarios, controls, and addressability | `internal/config/config_test.go` and both PRD example fixtures |
| 35.2-06–07 directional target order | `internal/config/config_test.go` |
| 35.2-08–09 candidate mutation and rollback verification | `internal/config/config_test.go` |
| 35.2-10–14 attribution and direct-failure precedence | `internal/verdicts/classifier_test.go` and `internal/orchestrator/state_test.go` |
| 35.2-15 safe public demo | `examples/rollout-demo/harness/main_test.go` and `run.sh safe` / `run.ps1 safe` |
| 35.2-16 mixed-failure public demo | `examples/rollout-demo/harness/main_test.go` and `run.sh mixed-failure` / `run.ps1 mixed-failure` |
| 35.2-17 rollback-failure public demo | `examples/rollout-demo/harness/main_test.go` and `run.sh rollback-failure` / `run.ps1 rollback-failure` |
| 35.2-18 prepared rollback | `examples/rollout-demo/harness/main_test.go` and `run.sh prepared-rollback` / `run.ps1 prepared-rollback` |
| 35.3-01–05 resource labeling, loopback ports, health, and Compose defaults | `internal/compose/manager_test.go`, `internal/dockerops/client_test.go`, and rollout-demo leak/endpoint assertions |
| 35.3-06–09 unsafe YAML, remote Docker, and confined candidate paths | `internal/config/config_test.go`, `internal/config/path_test.go`, and `internal/preflight/preflight_test.go` |
| 35.3-10–13 cleanup, active-checkout isolation, and retention | `internal/cleanup/registry_test.go`, `internal/gitops/repository_test.go`, `internal/artifacts/session_test.go`, and all demo leak checks |
| 35.3-14 trust and CI boundary | `.github/workflows/backline-ci.yml`, `docs/ci-security.md`, and `docs/safety-and-trust.md` |
| 35.4-01–02 failed-step and component diagnostics | `internal/reporting/reporting_test.go`, `internal/components/manager_test.go`, and public failure demos |
| 35.4-03 partial artifacts | `internal/artifacts/session_test.go` and `internal/orchestrator/state_test.go` |
| 35.4-04 schemas | `internal/contracts/schema_test.go`, `schemas/summary-v1.schema.json`, and `schemas/event-v1.schema.json` |
| 35.4-05–07 summary, redacted config, image and environment metadata | `internal/reporting/reporting_test.go`, `internal/artifacts/session_test.go`, and demo harness artifact assertions |
| 35.4-08–09 secret and scenario/handoff exclusion | `internal/redact/redact_test.go`, `internal/artifacts/session_test.go`, and handoff demo assertions |
| 35.4-10 truncation | `internal/process/runner_test.go` and `internal/reporting/reporting_test.go` |
| 35.4-11 cleanup state separation | `internal/verdicts/classifier_test.go` and `internal/reporting/reporting_test.go` |
| 35.4-12 no artifact pruning | `internal/artifacts/session_test.go` |
| 35.5-01 installation | release workflow plus `README.md` installation and checksum instructions |
| 35.5-02 doctor remediation | `internal/cli/cli_test.go` and `internal/preflight/preflight_test.go` |
| 35.5-03 non-mutating validation | `internal/preflight/preflight_test.go` and `internal/config/config_test.go` |
| 35.5-04–05 examples | minimal configuration in `README.md`; PRD examples exercised by `internal/config/config_test.go` |
| 35.5-06 demo duration | deterministic rollout-demo entry points and `examples/rollout-demo/README.md` |
| 35.5-07 standalone binary | `go.mod`, release workflow, and `README.md` quick start |
| 35.5-08 component addressing and headless support | `internal/components/manager_test.go` and `docs/config-reference.md` |
| 35.5-09 language-neutral workloads | `internal/workloads/executor_test.go` and `docs/workload-authoring.md` |
| 35.5-10 shared handoff | handoff demo and `docs/lifecycle-model.md` |
| 35.5-11 command replacement | `internal/components/manager_test.go` and `internal/hooks/executor_test.go` |
| 35.6-01 Linux Docker E2E | required `docker-e2e` job in `.github/workflows/backline-ci.yml` |
| 35.6-02 cross-platform unit tests | Linux, macOS, and Windows unit matrix in `.github/workflows/backline-ci.yml` |
| 35.6-03–04 JSON/JSONL/JUnit contracts | `internal/contracts/schema_test.go` and `internal/reporting/reporting_test.go` |
| 35.6-05 releases and checksums | `.github/workflows/release.yml` and local five-target dry run |
| 35.6-06 no silent required skips | unconditional Linux Docker E2E after the explicit trusted-fork gate |
| 35.6-07 analysis and security | vet, race, govulncheck, and gitleaks CI jobs |
| 35.6-08 documentation coverage | README plus all topic documents linked from it |
| 35.6-09–10 no placeholders or core TODOs | repository audit and `go test ./...` |

The public and advanced demo commands, observed verdicts, and artifact locations are recorded in `examples/rollout-demo/README.md`. CI always uploads their run artifacts and JUnit XML, including on failure.
