# Backline Rollout Compatibility Verifier Plan

## Status legend

- `[ ]` pending
- `[>]` active
- `[!]` blocked by an external prerequisite
- `[x]` completed and verified

Only one step may be active. A step advances only after its verification command or artifact succeeds.

## Execution tracker

1. `[x]` Preserve legacy state and replace governing documents
   - Verify: annotated legacy tag resolves to `d21cbd2`; governance describes only the replacement product.
2. `[x]` Establish Go CLI, contracts, schemas, and repository skeleton
   - Verify: Go build plus CLI contract tests.
3. `[x]` Implement committed configuration loading and preflight
   - Verify: config/Git/Compose validation tests and non-mutating command smoke tests.
4. `[x]` Implement safe process, artifacts, redaction, and cleanup foundations
   - Verify: cancellation, truncation, redaction, ownership, signal, and worktree tests.
5. `[x]` Implement Git, Compose, image, and component lifecycle
   - Verify: Docker integration tests for builds, networks, readiness, ports, labels, and cleanup.
6. `[x]` Implement workloads, controls, orchestration, and verdict classification
   - Verify: state-machine, attribution, continuation, rollback, and exit-code tests.
7. `[x]` Implement reporting and acceptance fixtures
   - Verify: schemas, golden artifacts, three demos, prepared rollback, and leak checks.
8. `[x]` Remove the obsolete product and rewrite documentation/site
   - Verify: repository audit, Go/docs/site checks, and no active legacy contract.
9. `[x]` Replace CI and add release automation
   - Verify: workflow validation and local cross-build/checksum dry run.
10. `[x]` Full acceptance audit
    - Verify: complete unit/integration/E2E/site/security suite and PRD traceability.

## Locked implementation decisions

- Branch: `codex/rollout-compatibility-verifier`.
- Legacy tag: `legacy/api-regression-ledger-v1` at `d21cbd2`.
- Go module: `github.com/ChimdumebiNebolisa/Backline`, Go 1.26.
- Standard-library CLI dispatcher; `yaml.v3` for strict YAML; platform support only where required.
- Git, Docker, and Compose CLI wrappers; no Docker SDK.
- Sequential builds and workloads for deterministic evidence.
- No compatibility layer, data migration, or in-tree legacy directory.
- Existing user-owned untracked directories are out of scope.

## Final verification

```text
gofmt check
go vet ./...
go test ./...
go test -race ./...
three rollout demo cases
prepared rollback integration fixture
site typecheck, lint, content, browser, and Lighthouse checks
cross-build five release targets and verify SHA-256 manifest
secret and dependency vulnerability scans
repository legacy-contract audit
```
