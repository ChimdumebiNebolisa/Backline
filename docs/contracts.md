# Backline cross-module contracts

This document is the authoritative contract reference for behavior shared across CLI, API, worker, and libraries.

## Run lifecycle

Valid run statuses: `QUEUED`, `RUNNING`, `PASSED`, `FAILED`, `ERROR`, `CANCELLED`.

Typical transitions:

```txt
QUEUED -> RUNNING -> PASSED | FAILED | ERROR
QUEUED -> RUNNING -> QUEUED   (worker retry)
RUNNING -> QUEUED | ERROR     (stale recovery)
QUEUED | RUNNING -> CANCELLED (explicit cancel)
```

Terminal statuses: `PASSED`, `FAILED`, `ERROR`, `CANCELLED`.

## Run submission

- `POST /api/runs` requires an existing project slug.
- Optional `idempotencyKey`: duplicate key returns the same run row.
- New runs start in `QUEUED` with `next_attempt_at <= now()`.

## Worker claim and retry

Claim ordering:

```sql
ORDER BY queued_at ASC
FOR UPDATE SKIP LOCKED
LIMIT 1
```

- Worker increments `attempt_count` on claim.
- Worker/runtime failures may requeue to `QUEUED` until `maxAttempts`.
- Assertion failures are check failures, not retryable worker faults.
- `persistResultsAndFinalize` writes all check results and terminal status in one transaction.

## Assertion JSON contract

Persisted assertion objects must include `path` when an operator is set.

Supported single-operator rules:

| Operator | JSON field | Value type |
|----------|------------|------------|
| equals | `equals` | any JSON |
| exists | `exists` | boolean |
| not equals | `not_equals` | any JSON |
| contains | `contains` | string/number |
| regex | `regex` | string pattern |
| gt / gte / lt / lte | `gt`, `gte`, `lt`, `lte` | number |

Example:

```json
{"path":"$.id","equals":1}
```

## Diff baseline strategies

| Strategy | Meaning |
|----------|---------|
| `PREVIOUS_COMPLETED` | Latest prior run in `PASSED` or `FAILED` for same project/environment |
| `LAST_PASSED` | Latest prior run in `PASSED` |
| `FIXED_RUN` | Explicit baseline run ID via `fixedRunId` query parameter |

`ERROR` and `CANCELLED` runs are never used as baselines.

## Pagination contract

List endpoints use absolute offset semantics:

- `limit`: page size (1-100)
- `offset`: number of rows to skip from the start of the filtered result set

Response `page` metadata includes `limit`, `offset`, and `total`.

## Error contract

API errors use:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "human-readable message",
    "field": "optional.field"
  }
}
```

CLI maps API failures to actionable messages and non-zero exit codes.

## Security-sensitive behavior

- Redact `Authorization`, `Cookie`, and `Set-Cookie` from logs and stored previews.
- Response previews are bounded (4096 bytes by default).
- Config must not execute shell commands or perform arbitrary file writes.
