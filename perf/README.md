# Backline Performance Testing

This directory contains the local performance harness for Backline. `k6` covers the HTTP pressure points, and `scripts/queue-load.ps1` verifies queue draining, duplicate protection, and post-load history/report behavior against the real API, worker, and sample app.

This harness is local-first. It is meant to prove basic local behavior, including two-worker queue contention, without making production capacity or scalability claims.

## Layout

- `data/projects.json`: shared project and check definitions for perf runs.
- `docker-compose.perf.yml`: adds a `k6` service on the same Docker network as `api`, `worker`, and `sample-api`.
- `k6/api-health.js`: load tests `/actuator/health` and `/api/health`.
- `k6/concurrent-run-submissions.js`: load tests concurrent `POST /api/runs`.
- `k6/slow-endpoint.js`: load tests the sample `/slow` endpoint.
- `k6/broken-endpoint.js`: load tests the sample `/broken` endpoint while treating `500` as expected.
- `scripts/queue-load.ps1`: submits a run batch, waits for terminal statuses, checks for stuck work and duplicate processing, and verifies `history` and `report`.
- `run-local.ps1`: end-to-end PowerShell runner for smoke, small, multi-worker, or full profiles.

## Outputs

Every run writes local artifacts to `perf/out/`:

- `*-summary.txt`: compact k6 proof summaries kept for review
- `*-summary.json`: optional local detail, ignored by git
- `queue-load-*.json`: queue and verification summary
- `history-*.txt`: optional local CLI history capture, ignored by git
- `report-*.md`: optional local generated reports, ignored by git

The committed proof set is intentionally small: `*-summary.txt` plus the `queue-load-smoke.json`, `queue-load-small.json`, and `queue-load-multi-worker.json` summaries.

## Typical runs

PowerShell, standalone:

```powershell
Copy-Item .env.example .env -ErrorAction SilentlyContinue
.\perf\run-local.ps1 -Profile smoke
.\perf\run-local.ps1 -Profile small
.\perf\run-local.ps1 -Profile multi-worker
```

Warm-stack sequence:

```powershell
.\perf\run-local.ps1 -Profile smoke -LeaveServicesRunning
.\perf\run-local.ps1 -Profile small -SkipComposeUp -SkipCliBuild
.\perf\run-local.ps1 -Profile multi-worker -SkipComposeUp -SkipCliBuild
docker compose -f docker-compose.yml -f perf/docker-compose.perf.yml --profile demo down
```

The standalone form is simpler. The warm-stack form avoids rebuilding and restarting between profiles.

The `multi-worker` profile uses `docker compose up --scale worker=2` and records which worker containers claimed runs in `queue-load-multi-worker.json`.

If you want to start services yourself:

```powershell
docker compose -f docker-compose.yml -f perf/docker-compose.perf.yml --profile demo up --build -d
.\gradlew.bat :apps:cli:installDist
```

Then run individual tests:

```powershell
docker compose -f docker-compose.yml -f perf/docker-compose.perf.yml --profile demo --profile perf run --no-deps --rm -e PERF_LABEL=adhoc k6 run /perf/k6/api-health.js
docker compose -f docker-compose.yml -f perf/docker-compose.perf.yml --profile demo --profile perf run --no-deps --rm -e PERF_LABEL=adhoc k6 run /perf/k6/concurrent-run-submissions.js
.\perf\scripts\queue-load.ps1 -RepoRoot (Get-Location) -Label adhoc
```

## Expected sample outcomes

The sample queue project intentionally includes failing checks against endpoints such as `/broken` and `/schema-change`. Because of that, queue verification is expected to end with terminal `FAILED` runs while still being a passing perf harness result.

What matters is:

- every submitted run reaches a terminal state
- each run writes the expected result rows
- no run is stuck in `QUEUED` or `RUNNING`
- no duplicate claims or duplicate `check_results` rows appear
- `backline history` and `backline report` still work after load

## What the tests prove

- `api-health.js`: the API and Actuator health endpoints stay responsive under concurrent reads.
- `concurrent-run-submissions.js`: the API can accept overlapping run submissions without excessive `5xx` or malformed run payloads.
- `queue-load.ps1`: queued runs move to terminal states, write the expected result rows, avoid duplicate claims/results, and still support `history` plus Markdown report generation after load.
- `multi-worker` profile: starts `worker=2`, submits a larger queue batch, and requires claims from at least two distinct worker containers in addition to the normal duplicate/stuck-run checks.
- `slow-endpoint.js`: the sample slow target stays reachable and returns the expected payload under load.
- `broken-endpoint.js`: the sample broken target consistently returns the intentional `500` payload under load instead of timing out or mutating shape.

## What to inspect

- `http_req_failed` rate
- `http_req_duration` `p(95)` and `p(99)`
- `checks` success rate
- final run status counts from `queue-load-*.json`
- `minimumClaimWorkers`, `distinctClaimWorkers`, `claimWorkerCounts`, `stuckRows`, `duplicateRows`, `repeatedClaimRows`, and `resultCountMismatches` in `queue-load-*.json`
- `history-*.txt` and generated `report-*.md` files referenced from `queue-load-*.json` when you need deeper local debugging

## Default pass/fail criteria

- Health and submission tests fail if API `5xx`/request failures become excessive (`http_req_failed >= 1%`) or latency crosses the script thresholds.
- Queue verification fails if any submitted run stays in `QUEUED` or `RUNNING`, if any run is claimed more than once in the stable local scenario, if duplicate `check_results` rows appear, or if a terminal run is missing expected result rows.
- The `multi-worker` profile also fails if fewer than two distinct worker containers claim work from the submitted batch.
- Post-load verification fails if `backline history` misses submitted runs or if `backline report` cannot generate Markdown after load.

## Limits

- The thresholds are intentionally modest and local-machine friendly.
- The multi-worker proof demonstrates local two-worker contention only.
- Treat the stored artifacts as the source of truth instead of relying on assumptions.
