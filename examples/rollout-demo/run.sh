#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
binary="$(mktemp "${TMPDIR:-/tmp}/backline-demo.XXXXXX")"
trap 'rm -f "$binary"' EXIT
cd "$root"
go build -o "$binary" ./cmd/backline

case_name="${1:-all}"
if [[ "$case_name" == "all" ]]; then
  cases=(safe mixed-failure rollback-failure)
else
  cases=("$case_name")
fi
for name in "${cases[@]}"; do
  args=(--case "$name" --backline "$binary" --fixture ./examples/rollout-demo/fixture)
  if [[ -n "${BACKLINE_DEMO_ARTIFACT_ROOT:-}" ]]; then
    args+=(--artifact-root "$BACKLINE_DEMO_ARTIFACT_ROOT")
  fi
  go run ./examples/rollout-demo/harness "${args[@]}"
done
