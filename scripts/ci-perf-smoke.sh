#!/usr/bin/env bash
# Perf smoke for Linux CI (Q13). Requires full stack from ci-e2e-demo or docker compose.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "${BACKLINE_SKIP_PERF_SMOKE:-}" == "true" ]]; then
  echo "Perf smoke skipped (BACKLINE_SKIP_PERF_SMOKE=true)"
  exit 0
fi

export PATH="$ROOT_DIR/apps/cli/build/install/backline/bin:${PATH:-}"
export BACKLINE_API_URL="${BACKLINE_API_URL:-http://localhost:8080}"

if ! curl -fsS "$BACKLINE_API_URL/actuator/health" >/dev/null 2>&1; then
  echo "Perf smoke: API not reachable; run ./scripts/ci-e2e-demo.sh stack first or set BACKLINE_SKIP_PERF_SMOKE=true"
  exit 1
fi

./gradlew :apps:cli:installDist -x test --no-daemon -q

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT
cp examples/sample-api/backline.yml "$WORKDIR/backline.yml"
cd "$WORKDIR"

RUN_LOG="$ROOT_DIR/build/perf-smoke-run.log"
mkdir -p "$(dirname "$RUN_LOG")"

for i in 1 2 3; do
  backline run --timeout-seconds 120 --idempotency-key "perf-smoke-$i" | tee -a "$RUN_LOG"
done

if grep -q "Timed out" "$RUN_LOG"; then
  echo "Perf smoke failed: run timed out"
  exit 1
fi

backline history >/dev/null
echo "Perf smoke completed (3 runs submitted and history OK)"
