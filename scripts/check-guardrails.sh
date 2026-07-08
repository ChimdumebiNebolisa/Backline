#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

grep_repo() {
  grep -R -n -E "$1" "$2" 2>/dev/null || true
}

echo "==> Guardrail: CLI must not import JDBC"
if grep_repo 'org\.springframework\.jdbc|java\.sql\.Connection|javax\.sql\.DataSource' apps/cli/src/main/java | grep -q .; then
  echo "FAIL: CLI imports JDBC types"
  exit 1
fi

echo "==> Guardrail: reporting must not import persistence"
if grep_repo 'jakarta\.persistence|org\.springframework\.data\.jpa|org\.springframework\.jdbc' libs/reporting/src/main/java | grep -q .; then
  echo "FAIL: reporting imports persistence"
  exit 1
fi

echo "==> Guardrail: response preview limit documented consistently"
PREVIEW_LIMIT="$(grep -o '4096' libs/core/src/main/java/dev/backline/core/constants/ResponseLimits.java | head -1 || true)"
DOC_LIMIT="$(grep -o '4096' docs/known-limitations.md | head -1 || true)"
if [[ -z "$PREVIEW_LIMIT" || -z "$DOC_LIMIT" ]]; then
  echo "FAIL: could not verify response preview limit alignment"
  exit 1
fi

echo "==> Guardrail: no shell execution from config parsing"
if grep_repo 'Runtime\.getRuntime\(\)\.exec|ProcessBuilder' libs/config/src/main/java | grep -q .; then
  echo "FAIL: config module appears to execute shell commands"
  exit 1
fi

echo "Guardrail checks passed."
