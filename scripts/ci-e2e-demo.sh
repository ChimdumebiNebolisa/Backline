#!/usr/bin/env bash
# Headless full-stack demo for CI and local verification (PLAN Q5a + Q5b).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ARTIFACT_DIR="${BACKLINE_E2E_ARTIFACT_DIR:-$ROOT_DIR/build/e2e-artifacts}"
mkdir -p "$ARTIFACT_DIR"

API_PID=""
WORKER_PID=""
SAMPLE_PID=""
POSTGRES_CID=""
TMUX_API=""
TMUX_WORKER=""
TMUX_SAMPLE=""

log() { echo "==> $*"; }

wait_for_url() {
  local url="$1"
  local label="$2"
  local attempts="${3:-90}"
  local i=1
  while [[ "$i" -le "$attempts" ]]; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      log "$label is up ($url)"
      return 0
    fi
    sleep 2
    i=$((i + 1))
  done
  echo "Timed out waiting for $label at $url" >&2
  return 1
}

wait_for_tcp() {
  local host="$1"
  local port="$2"
  local label="$3"
  local attempts="${4:-60}"
  local i=1
  while [[ "$i" -le "$attempts" ]]; do
    if (echo >/dev/tcp/"$host"/"$port") >/dev/null 2>&1; then
      log "$label is accepting connections on $host:$port"
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  echo "Timed out waiting for $label on $host:$port" >&2
  return 1
}

stop_tmux_session() {
  local session="$1"
  if [[ -n "$session" ]] && tmux has-session -t "=$session" 2>/dev/null; then
    tmux send-keys -t "$session:0.0" C-c
    sleep 2
    tmux kill-session -t "$session" 2>/dev/null || true
  fi
}

cleanup() {
  stop_tmux_session "$TMUX_API"
  stop_tmux_session "$TMUX_WORKER"
  stop_tmux_session "$TMUX_SAMPLE"
  [[ -n "$API_PID" ]] && kill "$API_PID" 2>/dev/null || true
  [[ -n "$WORKER_PID" ]] && kill "$WORKER_PID" 2>/dev/null || true
  [[ -n "$SAMPLE_PID" ]] && kill "$SAMPLE_PID" 2>/dev/null || true
  if [[ -n "$POSTGRES_CID" ]]; then
    docker rm -f "$POSTGRES_CID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for ci-e2e-demo.sh" >&2
  exit 1
fi

export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/backline}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-backline}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-backline}"
export BACKLINE_API_URL="${BACKLINE_API_URL:-http://localhost:8080}"

log "Starting PostgreSQL 16"
POSTGRES_CID="$(docker run -d --rm \
  -e POSTGRES_DB=backline \
  -e POSTGRES_USER=backline \
  -e POSTGRES_PASSWORD=backline \
  -p 5432:5432 \
  postgres:16-alpine)"
wait_for_tcp 127.0.0.1 5432 "PostgreSQL"

log "Building CLI distribution"
./gradlew :apps:cli:installDist -x test --no-daemon -q
export PATH="$ROOT_DIR/apps/cli/build/install/backline/bin:$PATH"

start_boot_run() {
  local module="$1"
  local session="$2"
  local extra_args="${3:-}"
  tmux has-session -t "=$session" 2>/dev/null || tmux new-session -d -s "$session" -c "$ROOT_DIR" -- "${SHELL:-bash}" -l
  if [[ -n "$extra_args" ]]; then
    tmux send-keys -t "$session:0.0" "./gradlew $module:bootRun --no-daemon --args=\"$extra_args\"" C-m
  else
    tmux send-keys -t "$session:0.0" "./gradlew $module:bootRun --no-daemon" C-m
  fi
}

log "Starting API, worker, and sample API"
start_boot_run ":apps:api" "e2e-api"
TMUX_API="e2e-api"
start_boot_run ":apps:worker" "e2e-worker" "--spring.main.keep-alive=true"
TMUX_WORKER="e2e-worker"
start_boot_run ":apps:sample-api" "e2e-sample"
TMUX_SAMPLE="e2e-sample"

wait_for_url "$BACKLINE_API_URL/actuator/health" "API"
wait_for_url "http://localhost:8081/health" "Sample API"

log "Q5a: doctor"
backline doctor

log "Q5a: verify sample config present"
[[ -f examples/sample-api/backline.yml ]] || { echo "missing examples/sample-api/backline.yml" >&2; exit 1; }

log "Q5a: submit and wait for run"
(
  cd examples/sample-api
  set +o pipefail
  backline run --timeout-seconds 120 2>&1 | tee "$ARTIFACT_DIR/run-q5a.log"
  run_exit=${PIPESTATUS[0]}
  if [[ "$run_exit" -eq 4 ]]; then
    echo "Run timed out" >&2
    exit 1
  fi
  # Exit 1 (FAILED) is expected: sample config includes broken-endpoint.
  exit 0
)
RUN_ID="$(grep -E '^RUN_ID:' "$ARTIFACT_DIR/run-q5a.log" | tail -1 | awk '{print $2}')"
[[ -n "$RUN_ID" ]] || { echo "RUN_ID not printed" >&2; exit 1; }
log "Captured RUN_ID=$RUN_ID"

log "Q5a: history"
backline history | tee "$ARTIFACT_DIR/history.log"

log "Q5a: diff"
backline diff "$RUN_ID" | tee "$ARTIFACT_DIR/diff.log"

REPORT_MD="$ARTIFACT_DIR/backline-report-${RUN_ID}.md"
log "Q5a: markdown report"
backline report "$RUN_ID" -o "$REPORT_MD"
[[ -f "$REPORT_MD" ]] || { echo "Markdown report missing" >&2; exit 1; }

log "Q5b: JSON report"
REPORT_JSON="$ARTIFACT_DIR/backline-report-${RUN_ID}.json"
backline report "$RUN_ID" --json-output "$REPORT_JSON"
[[ -f "$REPORT_JSON" ]] || { echo "JSON report missing" >&2; exit 1; }
grep -q "\"run\"" "$REPORT_JSON" || { echo "JSON report missing run payload" >&2; exit 1; }

log "Q5b: diff with LAST_PASSED baseline"
backline diff "$RUN_ID" --baseline LAST_PASSED | tee "$ARTIFACT_DIR/diff-last-passed.log"

log "Q5b: second run for policy enforcement"
(
  cd examples/sample-api
  set +e
  backline run --enforce-policy --baseline LAST_PASSED --timeout-seconds 120 | tee "$ARTIFACT_DIR/run-q5b-policy.log"
  POLICY_EXIT=$?
  set -e
  if [[ "$POLICY_EXIT" -eq 4 ]]; then
    echo "Policy run timed out" >&2
    exit 1
  fi
  log "Policy run exit code: $POLICY_EXIT (non-zero expected for failing sample checks)"
)

if [[ "${BACKLINE_RUN_PERF_SMOKE:-}" == "true" ]]; then
  log "Q13: perf smoke (3 runs)"
  for i in 1 2 3; do
    (
      cd examples/sample-api
      backline run --timeout-seconds 120 --idempotency-key "perf-smoke-$i" >>"$ARTIFACT_DIR/perf-smoke.log" 2>&1 || true
    )
  done
  backline history >>"$ARTIFACT_DIR/perf-smoke.log"
fi

log "E2E demo completed successfully"
log "Artifacts: $ARTIFACT_DIR"
