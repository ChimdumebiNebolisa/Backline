#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "==> Guardrail: CLI must not import JDBC"
if rg -q "org\\.springframework\\.jdbc|java\\.sql\\.Connection|javax\\.sql\\.DataSource" apps/cli/src/main/java; then
  echo "FAIL: CLI imports JDBC types"
  exit 1
fi

echo "==> Guardrail: reporting must not import persistence"
if rg -q "jakarta\\.persistence|org\\.springframework\\.data\\.jpa|org\\.springframework\\.jdbc" libs/reporting/src/main/java; then
  echo "FAIL: reporting imports persistence"
  exit 1
fi

echo "==> Guardrail: response preview limit documented consistently"
PREVIEW_LIMIT="$(rg -o '4096' libs/core/src/main/java/dev/backline/core/constants/ResponseLimits.java | head -1 || true)"
DOC_LIMIT="$(rg -o '4096' docs/known-limitations.md | head -1 || true)"
if [[ -z "$PREVIEW_LIMIT" || -z "$DOC_LIMIT" ]]; then
  echo "FAIL: could not verify response preview limit alignment"
  exit 1
fi

echo "==> Guardrail: no shell execution from config parsing"
if rg -q "Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder" libs/config/src/main/java; then
  echo "FAIL: config module appears to execute shell commands"
  exit 1
fi

echo "Guardrail checks passed."
