# Reviewer demo script

Short path to prove the full stack end-to-end. Assumes **Docker Desktop** (or compatible engine) is running and **Java 21** is installed.

1. **`cp .env.example .env`** — Creates local env defaults for Compose and tools. **Outcome:** `.env` exists with Postgres and Backline-related variables.

2. **`./gradlew clean build`** (Windows: `.\gradlew.bat clean build`) — Compiles all modules and runs tests. **Outcome:** build finishes successfully (or note any failing module before demoing).

3. **`docker compose up --build -d`** — Starts Postgres, API, and worker. **Outcome:** containers are healthy; API Flyway migrations apply on first start.

4. **`./gradlew :apps:cli:installDist`** — Produces the `backline` launcher under `apps/cli/build/install/cli/bin/` once the CLI module registers Gradle’s **Application** plugin (Task 4). If this task is missing, run `./gradlew :apps:cli:build` and pick up the launcher from the integration pass. **Outcome:** CLI binary exists.

5. **Add CLI to `PATH`** — Unix: `export PATH="$PWD/apps/cli/build/install/cli/bin:$PATH"`. PowerShell: `$env:Path = "$PWD\apps\cli\build\install\cli\bin;$env:Path"`. **Outcome:** `backline` resolves in the shell.

6. **`backline doctor`** — Verifies config and API reachability (`BACKLINE_API_URL`). **Outcome:** doctor reports API OK when `http://localhost:8080` is reachable.

7. **`backline sample init`** — Materializes sample files (skips if already present, depending on CLI flags). **Outcome:** `examples/sample-api/backline.yml` exists for editing or reference.

8. **`backline sample serve`** *or* **`docker compose --profile demo up -d sample-api`** — Runs the sample HTTP service on **8081**. **Outcome:** `curl http://localhost:8081/health` returns JSON with `"status":"UP"`.

9. **`cd examples/sample-api && backline run`** — Submits a run from the canonical config. **Outcome:** run completes with **at least one failed check** (`broken-endpoint` vs intentional 500) and passing checks for health and user fetch.

10. **`backline history`** — Lists recent runs from the API. **Outcome:** the new run appears with expected status.

11. **`backline diff <runId>`** — Shows regression diff vs the previous completed run. **Outcome:** structured diff output (or a clear message when no previous baseline exists).

12. **`backline report <runId>`** — Writes a Markdown report and prints its path. **Outcome:** report file on disk contains run summary, check summary, failures, latency, diff, limitations link, and timestamp.

For raw HTTP debugging of the same data, use [api-examples.md](api-examples.md).
