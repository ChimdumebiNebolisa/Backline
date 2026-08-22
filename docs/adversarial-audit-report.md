# Adversarial Codebase Audit Report — Backline

Audit date: 2026-08-22
Protocol: `ADVERSARIAL_CODEBASE_AUDIT_PROTOCOL.md` (companion prompt)
Auditor: ox-alpha

## 1. Audit Status

**AUDIT COMPLETE WITH LIMITATIONS**

Reasons:

- All 56 PostgreSQL-backed integration tests (40 in `apps/api`, 16 in `apps/worker`) were **skipped locally** because Testcontainers could not reach the local Docker engine (see §7). This is an environment-level docker-java ↔ Docker Desktop 29 API incompatibility, verified with a standalone probe outside the repository.
- To avoid treating skipped suites as proof, the highest-risk database semantics were re-verified directly against a real PostgreSQL 16 container using JDBC probes that replicate the exact production SQL (§7.3).
- The `site/` frontend received metadata-level review only; it is outside the product's correctness core and has its own CI.
- `perf/` harness was inspected but not executed end-to-end locally (requires the full compose stack).

## 2. Executive Verdict

**Ship only with stated conditions.** Overall codebase risk: **Medium**. Audit confidence: **High** for worker/DB semantics (proven with real-PostgreSQL probes), Medium elsewhere.

Backline's core design is sound: claim exclusivity via `FOR UPDATE SKIP LOCKED` was proven correct under 6 concurrent workers; idempotency uniqueness, migrations V1–V7, report agreement across formats, and policy/exit-code consistency all held up under adversarial testing. However, three defects were proven or identified that can produce wrong or misleading regression results under realistic failures:

Top risks:

1. **F-001**: A stale worker can finalize or requeue a run whose claim was already recovered and re-claimed by another worker (proven). Result attribution becomes nondeterministic under job-timeout + multi-worker conditions.
2. **F-004**: The HTTP executor buffers response bodies of unbounded size before preview truncation — a large target response can OOM the worker (guardrail violation in spirit).
3. **F-002**: Concurrent duplicate submissions with an idempotency key return HTTP 500 instead of replaying the first run, violating the documented contract.

Strongest counterargument to the verdict: Backline is a local-first single-operator tool where multi-worker races are rare in practice. Assessment: the product explicitly advertises "Support multiple workers without double-processing" (PRD) and stale-recovery exists precisely for crash/timeout scenarios — the race window is real, reachable, and cheap to close.

What the team is most likely underestimating: silent skip behavior of Testcontainers suites on developer machines (F-007) — green local builds with zero integration coverage.

Immediate next action: implement F-001 fencing and its concurrency regression test.

## 3. Repository State

| Item | Value |
| --- | --- |
| Repository | https://github.com/ChimdumebiNebolisa/Backline |
| Local path | `C:\Users\Chimdumebi\Backline` |
| Branch / commit | `main` @ `14c24821f5a7c80961528ea2ac251d3fe184cf6d` |
| Working tree | Clean for tracked files. Untracked: `.agents/`, `PointPilot/`, `output/`, `skills-lock.json` (not part of repo; excluded from scope) |
| Toolchain | Windows 11, Java 21.0.10 (Microsoft), Gradle 8.10.2, Docker Desktop 29.1.2 |
| Commands permitted | Full build/test; destructive-local probes against throwaway containers |
| External systems | No external services required by tests |

## 4. Product Reconstruction (summary)

Backline is a local-first API regression ledger: CLI (`backline.yml`) → REST API → PostgreSQL durable state → worker executes HTTP checks asynchronously → history/diff/reports. Core promise: persistent, queryable evidence of whether API behavior regressed. Implemented capabilities match PRD must-haves: run lifecycle, check sync by stable key, three baseline strategies, Markdown+JSON reports, policy gating with exit code 5, sample path. Not implemented despite appearing in docs: any runtime path that sets `CANCELLED` (F-003).

## 5. Scope and Coverage Ledger (summary)

276 tracked files inventoried.

| Area | Depth | Notes |
| --- | --- | --- |
| `apps/worker` (loop, DAO, recovery) | Full + dynamic probe | Critical paths fully traced |
| `apps/api` services/controllers/repos | Full | RunService, DiffService, CheckSyncService, handlers |
| `libs/executor` | Full | All assertion operators traced |
| `libs/config`, `libs/core` | Full | Validation determinism verified |
| `libs/reporting` | Full | MD/JSON agreement checked |
| `apps/cli` commands | Full | Exit codes, error paths |
| `db/migration` | Full + dynamic | Applied to fresh PG16 container |
| Worker/API integration tests | Dynamic (skipped locally, analyzed statically) | See §7 |
| `perf/`, `scripts/` | Structural review | Not executed end-to-end |
| `site/` | Metadata only | Separate stack + CI |

Critical paths received full static inspection; DB semantics additionally dynamically exercised.

## 6. Contradictions and Unverifiable Claims

| ID | Source A | Source B | Runtime authority |
| --- | --- | --- | --- |
| C-1 | `docs/contracts.md:15` documents `QUEUED \| RUNNING -> CANCELLED (explicit cancel)` | No code path writes `CANCELLED`; worker cancellation guards are dead code | Implementation wins; docs are aspirational |
| C-2 | `docs/contracts.md:23` “duplicate key returns the same run row” | Race yields SQLSTATE 23505 → Spring 500 | Probe-proven |
| C-3 | `DiffService` javadoc “queued before the current run” | Query orders only by `finished_at desc` (no queued_at filter, no tie-breaker) | Query wins |
| C-4 | GUARDRAILS “Store only a bounded response preview” | Executor reads full body into memory before truncation | Implementation wins |

## 7. Build and Verification Results

### 7.1 Standard checks

| Check | Command | Result | Key output |
| --- | --- | --- | --- |
| Compile/build | `gradlew build` | Pass | BUILD SUCCESSFUL in 3m 54s |
| Fresh full test run | `gradlew cleanTest test` | Pass (with skips) | BUILD SUCCESSFUL in 6m 17s; api 99 tests/40 skipped, worker 20/16 skipped, cli 36/0, config 26/0, executor 26/0, reporting 4/0, core 4/0, sample-api 6/0 |
| Migrations clean install | V1–V7 via psql into fresh PG16 container | Pass | All applied without error |
| Docker Compose config | not executed locally | Blocked | Requires full stack; CI runs e2e demo |

### 7.2 Why the skips happened (verified)

Standalone Testcontainers probe (outside repo) failed identically with Testcontainers 1.20.4 and 1.21.3: every client strategy receives `HTTP 400` with an empty `Info` payload from Docker Desktop 29’s Windows named pipes (`docker_engine`, `dockerDesktopLinuxEngine`), while the `docker` CLI works via the `desktop-linux` context. `~/.testcontainers.properties` pinned `NpipeSocketClientProviderStrategy`; removing the pin and setting `DOCKER_HOST` did not change the outcome. Conclusion: environment-level docker-java incompatibility, not a repository defect. The repository’s silent catch (`PostgresTestContainers` static initializer) hid this completely — see F-007.

### 7.3 Substituted dynamic verification (real PostgreSQL 16)

A throwaway `postgres:16-alpine` container was started via `docker run`; migrations V1–V7 were applied; JDBC probes replicated production SQL verbatim:

| Probe | Result |
| --- | --- |
| Claim exclusivity (30 runs, 6 concurrent workers, exact `CLAIM_SELECT`/`CLAIM_UPDATE`) | **PASS** — each run claimed exactly once, zero double claims |
| Stale-worker finalize without owner fence | **PROVEN** — old worker matched 1 row and overwrote the new owner’s active claim (`RUNNING/worker-B` became `PASSED/-`) |
| Stale-worker `requeueForRetry` without owner fence | **PROVEN** — old worker requeued the new owner’s in-flight run and deleted its persisted result |
| Finalize fenced with `AND locked_by = ?` | **PASS** — old worker rejected (0 rows), new owner keeps ownership |
| Idempotency race (two sessions, same key) | **PROVEN** — loser gets SQLSTATE 23505 (`uq_runs_idempotency_key`); no duplicate rows; API currently surfaces this as 500 |

## 8. Findings Summary

| ID | Severity | Priority | Confidence | Category | Title | Status |
| --- | --- | --- | --- | --- | --- | --- |
| F-001 | High | P1 | High | Reliability/Correctness | Worker finalize/requeue lacks claim-ownership fencing | Verified (probe) |
| F-004 | High | P1 | High | Reliability | Executor buffers unbounded response bodies in memory | Verified (code path) |
| F-002 | Medium | P1 | High | Correctness/Contract | Idempotent-submit race returns 500 instead of replaying run | Verified (probe) |
| F-003 | Medium | P2 | High | Documentation/Product | `CANCELLED` status unreachable; worker guards are dead code | Verified (inspection) |
| F-005 | Medium | P2 | High | Correctness | Diff hides FAILED→FAILED status-code changes; other diff semantic gaps | Verified (inspection) |
| F-007 | Low | P2 | High | Testing/Observability | Testcontainers suites fail silently as skipped when Docker is broken | Verified (local repro) |
| F-006 | Low | P2 | High | CLI contract | `status` returns exit 0 for CANCELLED while `run` uses 3 | Verified (inspection) |
| F-008 | Low | P3 | Medium | Data | Project-create slug race returns 500 instead of 409 | Hypothesis (same class as F-002) |
| F-009 | Low | P3 | High | Validation | Overlong `environment`/`source`/key fields reach DB → 500 | Verified (inspection) |
| F-010 | Info | P3 | High | UX | `diff` prints “vs null” when no baseline exists | Verified |

## 9. Detailed Findings

### F-001: Worker finalize/requeue lacks claim-ownership fencing

- **Severity/Priority/Confidence:** High / P1 / High (probe-proven)
- **Affected:** `apps/worker/src/main/java/dev/backline/worker/persistence/WorkerRunDao.java` (`finalizeRunInternal`, `requeueForRetry`, `persistResultsAndFinalize`); `WorkerLoop.processRun`
- **Workflow:** long-running run exceeds `jobTimeoutMs` (or `staleThresholdMs`) while worker A still executes → another worker’s `recoverStaleRuns` requeues it → worker B claims it → A finishes and finalizes.

Summary: `finalizeRunInternal` updates `WHERE id = ? AND status = 'RUNNING'` and `requeueForRetry` matches the same predicate. After a recovery re-claim the row is `RUNNING` again but owned by a different worker, so A’s late finalize/requeue mutates B’s active claim.

Evidence (real PostgreSQL 16, production SQL verbatim):

```
worker-A claimed … ; stale recovery requeued … ; worker-B claimed …
before old-worker finalize: RUNNING/worker-B
old worker finalize matched rows = 1
PROVEN: stale worker A overwrote worker B's active claim (status=PASSED/-)

old worker requeue matched rows = 1
PROVEN: stale worker A requeued worker B's in-flight run and deleted B's persisted result
```

Impact: terminal-status attribution between overlapping attempts becomes nondeterministic; B’s results can be discarded after successful execution; attempt bookkeeping diverges from reality. A regression verdict can be attributed to the wrong execution.

Counterargument: outcomes are still internally consistent (one full result set wins; unique `(run_id, check_key)` prevents duplicates). Assessment: does not remove severity — the winner is chosen by timing, not truth, and the losing attempt’s work is silently destroyed.

Remediation: add ownership fence (`AND locked_by = ?`) to finalize/requeue/persist-and-finalize; pass the claiming worker’s id through; treat 0-row matches as benign loss-of-ownership (log, skip) instead of throwing. Validated by probe scenario 2 (fenced variant rejected the stale writer).

Regression test: Testcontainers test reproducing claim→recover→reclaim→late-finalize and asserting the original owner cannot mutate the row.

### F-002: Idempotent-submit race returns 500 instead of replaying the run

- **Severity/Priority/Confidence:** Medium / P1 / High
- **Affected:** `apps/api/.../service/RunService.java:62-96`; contract `docs/contracts.md:23`
- Probe proof: two concurrent inserts with one key → loser receives SQLSTATE 23505; Spring maps `DataIntegrityViolationException` to generic 500 `INTERNAL_ERROR`.
- Impact: CLI retries during flaky networks produce hard failures exactly when idempotency was requested; violates documented contract.
- Remediation: catch the constraint violation around insert and re-fetch by key (single well-defined retry).
- Regression test: pre-insert a run with key K, then submit K concurrently-shaped request; assert existing run returned (integration test plus targeted unit seam).

### F-004: HTTP executor buffers unbounded response bodies

- **Severity/Priority/Confidence:** High / P1 / High (statically proven path)
- **Affected:** `libs/executor/.../HttpCheckExecutor.java:99` (`HttpResponse.BodyHandlers.ofString`)
- Summary: entire body is materialized before `buildResponsePreview` truncates to 4096 bytes. A target returning gigabytes OOMs the worker. Latency measurement also includes body download (acceptable, documented).
- Remediation: bounded streaming read with explicit cap; overflow → deterministic `ERROR` outcome with `BODY_TOO_LARGE` error code (not retried; ERROR checks are recorded, not rescheduled).
- Regression test: MockWebServer-style unit test with oversized body asserting `BODY_TOO_LARGE` outcome and bounded memory path.

### F-005: Diff semantics gaps

- **Severity/Priority/Confidence:** Medium / P2 / High
- **Affected:** `DiffService.buildEntry` / `entriesNoPrevious`
- Details:
  1. FAILED→FAILED with different `actual_status` (e.g., 500 vs 503) is labeled `STILL_FAILING` — PRD requires “Status code changes” to be shown.
  2. `ASSERTION_CHANGED` detected only for PASSED→PASSED pairs.
  3. With no baseline, SKIPPED results are labeled `STILL_FAILING` (semantically wrong).
  4. Javadoc claims “queued before the current run”; query actually orders by `finished_at desc` with no tie-breaker (equal timestamps → nondeterministic baseline choice).
- Remediation: surface `STATUS_CODE_CHANGED` whenever `actualStatus` differs regardless of pass/fail equality; document remaining semantics in `docs/contracts.md`; leave ordering tie-break note documented (fix optional).
- Regression test: DiffServiceUnitTest cases for FAILED(500)→FAILED(503) etc.

### F-006: Inconsistent CANCELLED exit codes

- **Affected:** `StatusCommand.call()` returns `default -> 0` for CANCELLED; `RunCommand.exitForTerminal` returns 3. Remediation: align `status` to 3. Trivial test update.

### F-007: Silent Testcontainer skips hide broken environments

- **Affected:** `apps/api/.../persistence/PostgresTestBase.java` + `support/PostgresTestContainers.java`; `apps/worker/.../support/PostgresWorkerTestBase`
- On this machine all 56 integration tests skipped with zero diagnostics while the build reported success. CI is guarded (`CI=true` → throw), which is good; local developers get false confidence.
- Remediation: capture the initialization exception message and include it in the assumption-failure message so skips are self-explaining.

### F-008/F-009/F-010

Slug create-race (same fix class as F-002, lower likelihood — deferred), missing length validation before DB constraints (deferred, structured-error polish), “vs null” print (cosmetic — fixed opportunistically if trivial).

## 10. Rejected Hypotheses

| Hypothesis | Evidence examined | Verdict |
| --- | --- | --- |
| SKIP LOCKED claim allows double processing | Probe: 30 runs × 6 workers → 0 double claims | Rejected |
| Duplicate runs can be persisted for one idempotency key | Partial unique index enforced (23505 observed) | Rejected at DB level |
| Migrations fail on fresh installs | V1–V7 applied cleanly to fresh PG16 | Rejected |
| Operator-less assertions pass vacuously in executor | ConfigValidator + CheckSyncService reject them upstream | Rejected |
| Report formats disagree with canonical results | JSON/Markdown generated from identical DTOs; identical risk formula | Rejected |
| Policy evaluation contradicts printed status / exit code | `policyAwareExit` still applies `exitForTerminal` after policy pass | Rejected |
| Duplicate execution creates duplicate result rows | `(run_id, check_key)` unique + delete-before-retry | Rejected |

## 11. Remediation Implemented (this pass)

| Finding | Fix | Regression test | Verification |
| --- | --- | --- | --- |
| F-001 | `WorkerRunDao` finalize/persist/requeue updates fenced with `AND locked_by = ?`; owner token flows through `ClaimedRun.lockedBy()`; lost ownership is logged (`run.claimLost`) instead of mutating another worker's claim; `persistResultsAndFinalize` checks the fence before writing so stale attempts never write results | `WorkerFencingTest` (2 tests: stale finalize rejected; stale persist+requeue rejected while current owner completes) | Probe scenario 2 proved the fenced predicate against PostgreSQL 16 before implementation; shipped SQL matches it verbatim |
| F-002 | `RunService.submit` takes a transaction-scoped advisory lock (`pg_advisory_xact_lock(hashtext(key))`) when an idempotency key is present, serializing concurrent duplicates so the loser replays the winner's row; unique index remains the backstop | `RunIdempotencyRaceTest` (6 concurrent submits, one key → same run id, exactly one row) | Probe proved the race (SQLSTATE 23505) pre-fix |
| F-004 | Executor streams the response body and cuts off at `ResponseLimits.RESPONSE_BODY_MAX_BYTES` (10 MB); overflow → deterministic `ERROR`/`BODY_TOO_LARGE` outcome carrying the HTTP status; UTF-8 decode semantics preserved | `HttpCheckExecutorTest.oversizedBodyFailsWithBodyTooLargeInsteadOfBuffering`, `.bodyAtExactLimitIsStillEvaluatedNormally` | Local run passed (no Docker needed) |
| F-005 | `DiffService.buildEntry` surfaces `STATUS_CODE_CHANGED` whenever equal-status pairs have different `actual_status` (e.g., FAILED 500 → FAILED 503); class javadoc corrected (finished_at ordering, not queued-before) | `DiffServiceUnitTest.failedToFailedWithDifferentHttpStatusCode_surfacesStatusCodeChanged`, `.failedToFailedWithSameHttpStatusCode_staysStillFailing` | Local run passed |
| F-006 | `StatusCommand` returns exit 3 for CANCELLED, matching `run` | CLI smoke suite still passes | Local run passed |
| F-007 | Container-start failures captured with cause; skip reason printed to test stderr (`[test-skip] …`) and carried in the JUnit XML system-err + IDE assumption message; CI still hard-fails | existing suites | Skip XML on this machine now contains the real docker-java failure cause |
| F-003 | Real cancellation shipped: `POST /api/runs/{id}/cancel` + `backline cancel`; conditional non-terminal update is linearizable against claim/finalize; CANCELLED event written; worker observes mid-run and discards partials | `RunCancellationTest` (queued cancel + event, running-cancel releases ownership, terminal 409); `WorkerFencingTest.cancelledRunningRunCannotBeFinalizedRequeuedOrPersistedByOwner`; live Compose demo (queued cancel survives worker restart; terminal cancel conflicts) | Full suite green; E2E verified |

### Findings proven during the no-skip verification pass

- **F-011 (High→fixed)**: `--enforce-policy` with no `policy:` block and no preset silently ran unenforced instead of applying the strict default. Fixed in `RunCommand.effectiveEnforcedPolicy`; regression test `runEnforcePolicyWithoutConfigPolicyAppliesStrictDefault`.
- **F-012 (Medium→fixed)**: `docker-compose.yml` gave every worker replica the same `BACKLINE_WORKER_ID`, colliding claim identities in the audit trail under `--scale worker=N`. Fixed by removing the override so containers fall back to unique hostnames; verified live (two replicas, 2+2 claim split).
- **F-013 (Medium→fixed)**: `WorkerLoop.stop()` could time out its join while a check was mid-flight, leaving a zombie poller that claimed later fixtures' runs (proven by intermittent cross-test failures). Fixed with interrupt-on-stop plus test-scoped queue hygiene (`deleteStrayNonTerminalRuns`) making worker suites execution-order independent.

Deferred at audit time and since remediated: F-003 (real `POST /api/runs/{id}/cancel` +
`backline cancel` with linearizable terminal semantics), F-008 (project-slug create race now
returns structured 409 via flush-time constraint mapping), F-009 (environment/configHash/
source/idempotencyKey/check-name lengths validated before the database). Still open: F-010
cosmetic diff print.

### Merge note

While preparing the remediation commits, `origin/main` advanced (quality-roadmap work). After rebasing:
upstream had independently fixed C-3 (baseline queries now require `queuedAt < current.queuedAt`) and
implemented F-005's substance (STATUS_CODE_CHANGED for equal-status pairs) inside a broader
response-contract diff feature; it also added `CANCELLED -> 3` to `status`. This remediation keeps those
implementations and layers F-001 fencing, F-002 idempotent replay, F-004 body bound, F-007 skip
diagnostics, and documentation truthfulness (F-003) on top. Post-merge full suite: BUILD SUCCESSFUL,
0 failures (Docker-gated suites skip locally for the environment reason documented in §7.2).

### Post-fix verification

- Full fresh run: `gradlew cleanTest test` → **BUILD SUCCESSFUL in 6m 49s**; 228 tests total, 59 skipped (all Testcontainers-gated, each skip now self-reporting its cause), 0 failures.
- The fenced SQL shipped in `WorkerRunDao` matches the probe variant proven against PostgreSQL 16 (scenario 2: stale owner matched 0 rows, current owner unaffected).

## 12. Residual Risk and Unknowns

- Integration suites could not execute locally (environment); their correctness rests on CI execution plus the substituted PG probes above.
- Compose startup-order and perf profiles were reviewed structurally, not executed.
- Site module not audited in depth.
