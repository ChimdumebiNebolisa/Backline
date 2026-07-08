#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

failures=0

echo "==> Contract drift: README mentions core CLI commands"
for cmd in run history diff report doctor; do
  if ! grep -q "backline $cmd" README.md; then
    echo "FAIL README.md missing example for: backline $cmd"
    failures=$((failures + 1))
  fi
done
if ! grep -q "backline sample init" README.md; then
  echo "FAIL README.md missing: backline sample init"
  failures=$((failures + 1))
fi
if ! grep -qi "worker" README.md; then
  echo "FAIL README.md missing worker troubleshooting or command reference"
  failures=$((failures + 1))
fi

echo "==> Contract drift: api-examples documents health endpoints"
for path in "/actuator/health" "/api/health"; do
  if ! grep -q "$path" docs/api-examples.md; then
    echo "FAIL docs/api-examples.md missing $path"
    failures=$((failures + 1))
  fi
done

echo "==> Contract drift: enforce-policy documented"
if ! grep -q "enforce-policy" docs/ci-integration.md; then
  echo "FAIL docs/ci-integration.md missing enforce-policy"
  failures=$((failures + 1))
fi

if [[ "$failures" -gt 0 ]]; then
  echo "Contract drift check failed with $failures issue(s)"
  exit 1
fi

echo "Contract drift checks passed."
