# Backline API examples

Base URL in local development: `http://localhost:8080`.

Successful responses use a **`data`** wrapper (single resource) or **`data` + `page`** (lists). Errors use an **`error`** object with `code`, `message`, and optional `field`.

Replace UUIDs and query parameters with values from your environment.

---

## Health

### Actuator health

```bash
curl -sS http://localhost:8080/actuator/health
```

Example (shape may vary with database state):

```json
{
  "status": "UP"
}
```

### API health

```bash
curl -sS http://localhost:8080/api/health
```

Example:

```json
{
  "data": {
    "status": "UP"
  }
}
```

---

## Projects

### Create project

```bash
curl -sS -X POST http://localhost:8080/api/projects \
  -H 'Content-Type: application/json' \
  -d '{"slug":"sample-api","name":"Sample API"}'
```

Example response:

```json
{
  "data": {
    "id": "b2c3d4e5-f678-90ab-cdef-1234567890ab",
    "slug": "sample-api",
    "name": "Sample API",
    "createdAt": "2024-01-02T12:00:00Z",
    "updatedAt": "2024-01-02T12:00:00Z"
  }
}
```

### List projects (paginated)

```bash
curl -sS 'http://localhost:8080/api/projects?limit=25&offset=0'
```

Example:

```json
{
  "data": [
    {
      "id": "b2c3d4e5-f678-90ab-cdef-1234567890ab",
      "slug": "sample-api",
      "name": "Sample API",
      "createdAt": "2024-01-02T12:00:00Z",
      "updatedAt": "2024-01-02T12:00:00Z"
    }
  ],
  "page": {
    "limit": 25,
    "offset": 0,
    "total": 1
  }
}
```

### Get project by id

```bash
curl -sS http://localhost:8080/api/projects/b2c3d4e5-f678-90ab-cdef-1234567890ab
```

Example:

```json
{
  "data": {
    "id": "b2c3d4e5-f678-90ab-cdef-1234567890ab",
    "slug": "sample-api",
    "name": "Sample API",
    "createdAt": "2024-01-02T12:00:00Z",
    "updatedAt": "2024-01-02T12:00:00Z"
  }
}
```

---

## Check sync

### Sync checks for a project

```bash
curl -sS -X POST http://localhost:8080/api/checks/sync \
  -H 'Content-Type: application/json' \
  -d '{
  "projectSlug": "sample-api",
  "projectName": "Sample API",
  "checks": [
    {
      "key": "health",
      "name": "Health check",
      "method": "GET",
      "url": "http://localhost:8081/health",
      "expectedStatus": 200,
      "maxLatencyMs": 300,
      "assertions": []
    }
  ]
}'
```

Example (truncated):

```json
{
  "data": [
    {
      "id": "…",
      "projectId": "…",
      "key": "health",
      "name": "Health check",
      "method": "GET",
      "url": "http://localhost:8081/health",
      "expectedStatus": 200,
      "maxLatencyMs": 300,
      "active": true
    }
  ]
}
```

---

## Runs

### Submit a run

```bash
curl -sS -X POST http://localhost:8080/api/runs \
  -H 'Content-Type: application/json' \
  -d '{
  "projectSlug": "sample-api",
  "environment": "local",
  "configHash": "demo-config-hash",
  "idempotencyKey": null,
  "source": "curl-demo"
}'
```

Example:

```json
{
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "projectId": "b2c3d4e5-f678-90ab-cdef-1234567890ab",
    "environment": "local",
    "status": "QUEUED",
    "configHash": "demo-config-hash",
    "source": "curl-demo",
    "idempotencyKey": null,
    "queuedAt": "2024-01-02T12:05:00Z",
    "startedAt": null,
    "finishedAt": null,
    "attemptCount": 0
  }
}
```

### List runs (filters + pagination)

```bash
curl -sS 'http://localhost:8080/api/runs?projectSlug=sample-api&environment=local&status=FAILED&limit=25&offset=0'
```

Example:

```json
{
  "data": [],
  "page": {
    "limit": 25,
    "offset": 0,
    "total": 0
  }
}
```

### Get run by id

```bash
curl -sS http://localhost:8080/api/runs/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

### Get check results for a run

```bash
curl -sS http://localhost:8080/api/runs/a1b2c3d4-e5f6-7890-abcd-ef1234567890/results
```

Example:

```json
{
  "data": [
    {
      "id": "…",
      "runId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "checkId": "…",
      "checkKey": "health",
      "checkName": "Health check",
      "status": "PASSED",
      "actualStatus": 200,
      "latencyMs": 45,
      "errorCode": null,
      "errorMessage": null,
      "responsePreview": null,
      "assertions": [],
      "createdAt": "2024-01-02T12:05:10Z"
    }
  ],
  "page": {
    "limit": 1,
    "offset": 0,
    "total": 1
  }
}
```

### Get diff vs previous completed run

```bash
curl -sS http://localhost:8080/api/runs/a1b2c3d4-e5f6-7890-abcd-ef1234567890/diff
```

Example:

```json
{
  "data": {
    "runId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "previousRunId": null,
    "entries": []
  }
}
```

---

## Project summary

```bash
curl -sS http://localhost:8080/api/projects/b2c3d4e5-f678-90ab-cdef-1234567890ab/summary
```

Example:

```json
{
  "data": {
    "project": {
      "id": "b2c3d4e5-f678-90ab-cdef-1234567890ab",
      "slug": "sample-api",
      "name": "Sample API",
      "createdAt": "2024-01-02T12:00:00Z",
      "updatedAt": "2024-01-02T12:00:00Z"
    },
    "totalRuns": 3,
    "passedRuns": 1,
    "failedRuns": 2,
    "erroredRuns": 0,
    "lastRun": {
      "id": "…",
      "projectId": "b2c3d4e5-f678-90ab-cdef-1234567890ab",
      "environment": "local",
      "status": "FAILED",
      "configHash": "…",
      "source": "cli",
      "idempotencyKey": null,
      "queuedAt": "…",
      "startedAt": "…",
      "finishedAt": "…",
      "attemptCount": 1
    }
  }
}
```

---

## Check history

```bash
curl -sS 'http://localhost:8080/api/checks/c0ffee00-0000-4000-8000-000000000001/history?limit=25&offset=0'
```

Example:

```json
{
  "data": [],
  "page": {
    "limit": 25,
    "offset": 0,
    "total": 0
  }
}
```

---

## Structured validation error (shape)

```bash
curl -sS -X POST http://localhost:8080/api/projects \
  -H 'Content-Type: application/json' \
  -d '{"slug":"BAD_SLUG","name":""}'
```

Example:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "slug must be 1-120 lowercase letters, digits, or dashes",
    "field": "slug"
  }
}
```

---

## OpenAPI / Swagger UI

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html` (may redirect to `swagger-ui/index.html` depending on version).
