# Sample API checks

This directory holds the canonical `backline.yml` used by the demo. It targets the local **sample API** on port **8081**.

## Run the sample API

From the repository root:

```bash
./gradlew :apps:sample-api:bootRun
```

On Windows (PowerShell):

```powershell
.\gradlew.bat :apps:sample-api:bootRun
```

Alternatively, after building the CLI: `backline sample serve` (starts the same application).

## Run Backline checks

With the API server, worker, and PostgreSQL already running (see root [README.md](../../README.md)), execute checks from **this directory** so `backline.yml` is picked up:

```bash
backline run
```

## Expected outcome

- **health** and **get-user** are expected to **pass** (the sample endpoints return `200` and match assertions).
- **broken-endpoint** is expected to **fail**: the sample `/broken` route intentionally returns **500**, while the check requires **200**. This demonstrates a failing regression check without hiding errors.

For the full reviewer walkthrough, see [docs/demo-script.md](../../docs/demo-script.md).
