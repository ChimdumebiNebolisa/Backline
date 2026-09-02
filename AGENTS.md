# AGENTS.md

## Project

Backline is a local-first native Go CLI for mixed-version and rollback compatibility verification of trusted Dockerized stateful services.

The product contains:

- one `backline` binary;
- committed-candidate YAML configuration;
- Git worktree, Docker, and Compose orchestration;
- project-supplied lifecycle hooks and workloads;
- two compatibility verdicts and local diagnostic artifacts;
- deterministic demos and a static documentation site.

It contains no Backline server, database, worker, daemon, account, or long-term ledger.

Read `PRD.md`, `ARCHITECTURE.md`, `GUARDRAILS.md`, and `PLAN.md` before coding.

## Required security skill

Use the installed `vibe-security` skill for code that handles configuration, environment variables, secrets, project commands, paths, Docker, artifacts, or CI trust boundaries.

## Working agreements

- Think before coding and state only assumptions that affect the change.
- Treat PRD as scope, Architecture as ownership, Guardrails as enforcement, and Plan as the active tracker.
- Write the minimum code for the active step.
- Do not refactor unrelated code or touch user-owned untracked files.
- Keep errors explicit, validation deterministic, and package responsibilities narrow.
- Explain intent and non-obvious safety/attribution constraints; do not comment obvious code.
- Do not leave TODOs in required execution paths.

## Execution

- Only one `PLAN.md` step may be active.
- Reproduce failures when practical and verify every completed step with the narrowest meaningful check.
- If scope, ownership, or enforcement changes, update the corresponding governing document before code.
- Never claim completion without fresh command output.

## Go rules

- Use `gofmt` and standard Go conventions.
- Prefer explicit small interfaces at external command boundaries; do not create broad utility packages.
- Use argument arrays with `exec.CommandContext`; never concatenate configured input into a shell command.
- Keep orchestration separate from verdict classification and artifact rendering.
- Keep platform-specific process behavior behind build-tagged files.

## Safety rules

- Treat revisions and project commands as trusted code, but validate all Backline-controlled input.
- Never log secrets or full environments.
- Resolve paths/symlinks and reject boundary escapes.
- Label and register every owned resource; cleanup only exact current-run resources.
- Candidate YAML cannot enable unsafe Compose or remote Docker behavior.
- Preserve compatibility evidence even when reporting or cleanup later fails.
- Do not execute untrusted fork code with Docker access or secrets in standard CI.

## Verification

- Unit-test deterministic logic.
- Integration-test real Git and Docker behavior at their boundaries.
- Required Docker tests fail clearly when Docker is unavailable in CI.
- Test validation failures, operational failures, interruption, cleanup, redaction, and both verdict paths.
- Run the mandatory safe, mixed-failure, raw-rollback-failure, and prepared-rollback fixtures before final sign-off.

## Reporting

End implementation reports with:

- What changed
- Files changed
- Verification run
- Result
- Remaining uncertainty
