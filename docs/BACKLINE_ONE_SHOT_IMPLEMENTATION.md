# Backline one-shot implementation task

## Objective

Complete the remaining verified Backline roadmap work in one implementation task while preserving the existing PRD, architecture, guardrails, and backend quality work. The primary deliverable is an isolated, technically credible Backline landing site under `site/`, with repository documentation and CI evidence that its public claims match the implemented CLI, API, worker, database, sample API, and report generator.

This replaces the roadmap's daily stop-after-one-work-unit operating model. It does not expand product scope and it does not authorize a backend rewrite.

## Starting-point assumptions to verify

- The Java repository already contains the Q5-Q13 quality work described in `PLAN.md`, including CI E2E coverage, policy checks, property tests, guardrails, contract-drift checks, and performance smoke tooling.
- The existing `codex/daily-roadmap-inventory` branch contains the verified repository inventory and Gradle baseline record.
- `site/` does not yet exist and must remain independent from Gradle, PostgreSQL, the API, and the worker.

If an assumption is false, record the evidence and repair only the confirmed gap that is inside `PRD.md`, `ARCHITECTURE.md`, and `GUARDRAILS.md`.

## In scope

1. Reconcile the roadmap and progress record into this one-shot execution model.
2. Create a standalone Vite + TypeScript site in `site/` with its own package manifest, lockfile, source, tests, build, and README.
3. Build a responsive, keyboard-accessible landing page for backend engineers with:
   - verified navigation and repository links;
   - outcome-led positioning as a local-first API regression ledger;
   - verified CLI commands and representative output;
   - the actual queued → running → terminal run lifecycle;
   - durable history, diff, CI policy, architecture, limitations, and local setup sections.
4. Add site-only typecheck, lint, content, browser, and build verification without coupling backend CI to Node.
5. Reconcile documentation and record public-claim, isolation, verification, and GitHub evidence.

Backend phases in the source roadmap are audit-and-reconcile work unless a confirmed defect remains. Do not add speculative network policy, metrics, authentication, hosted-service, dashboard, release, or deployment behavior.

## Explicitly out of scope

- Authentication, authorization, cloud sync, SaaS hosting, web dashboards, load testing as a product feature, AI features, Kafka, Kubernetes, or plugins.
- Changing CLI commands, API contracts, exit codes, report formats, migrations, worker claiming, retry semantics, or database ownership solely to support the site.
- Publishing a deployment or release artifact without an explicitly selected host, canonical URL, or release process.

## Commit slices

Each commit must be independently reviewable, use a detailed body, and pass the narrowest relevant verification before the next slice starts.

1. `docs: define one-shot roadmap execution`
   - Add this task contract and reconcile the progress record.
   - Verify document scope against PRD, architecture, guardrails, and current GitHub state.
2. `feat(site): scaffold isolated static site`
   - Add `site/` package metadata, lockfile, Vite/TypeScript configuration, entrypoint, and README.
   - Verify `npm ci`, `npm run typecheck`, `npm run lint`, and `npm run build` without Gradle or backend services.
3. `feat(site): publish verified regression-ledger narrative`
   - Add the landing-page content, responsive visual system, navigation behavior, and verified product fixtures.
   - Verify command names, statuses, exit codes, links, semantic landmarks, focus states, and reduced-motion behavior.
4. `test(site): add content and browser smoke coverage`
   - Add deterministic content checks and Playwright browser checks for desktop, mobile navigation, keyboard focus, and reduced motion.
   - Verify the test suite against the built site.
5. `ci(site): verify site independently and record handoff`
   - Add path-filtered site CI and final documentation/progress evidence.
   - Verify backend Gradle remains site-independent, run the affected backend baseline, push the branch, update PR #13, and merge only after required checks pass.

## Acceptance criteria

- `site/` builds from its committed lockfile without PostgreSQL, Spring Boot, or Gradle.
- The root Gradle project has no `site/` include, Node invocation, or frontend dependency.
- The landing page is usable at mobile and wide layouts, has semantic landmarks, a skip link, visible focus, accessible navigation, and reduced-motion behavior.
- Every command, state, exit code, architecture component, and limitation shown publicly is traceable to checked-in CLI code, tests, README, runbook, API examples, or the sample configuration.
- Site verification and backend verification are recorded with exact commands and outcomes.
- The branch contains small, detailed commits; each is pushed, the PR is updated, and the verified result is merged to `main`.
- Deployment uses the selected Vercel project `backline-site` and the stable canonical URL `https://backline-site-xi.vercel.app`; subsequent releases must update this project instead of creating a new site.
- The production alias is browser-verified at desktop and mobile sizes before delivery is reported complete.

## Required final report

Report the selected task, files changed, commit and merge history, self-review corrections, exact verification commands and exit codes, public-claim evidence, blast radius, isolation evidence, deferred items, and any environment-specific uncertainty. Never describe a deployment, backend feature, or public claim as complete without evidence.
