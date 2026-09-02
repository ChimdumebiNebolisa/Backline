# Troubleshooting

## `backline validate` exits 21

Read every reported field/path error. Common causes are duplicate or unknown YAML keys, uncommitted candidate config, invalid target order, missing controls, unsafe Compose features, or an escaping symlink. Use `--verbose` for committed-ref and Compose normalization details.

## `backline doctor` exits 22

Confirm `git`, `docker`, and `docker compose` are installed; Docker is running; the selected context is local; configured host executables exist; and the artifact parent is writable.

## Docker Desktop reports an inaccessible `dockerInference` or `engine.sock`

This is a Docker Desktop Windows stale AF_UNIX socket problem, not a Backline resource. Fully quit Docker Desktop, stop orphaned Docker processes, terminate the `docker-desktop` WSL distribution, and quarantine the affected runtime directory before restarting. Avoid factory reset unless Docker data loss is acceptable.

## Verification exits 23

Inspect `operational_errors`, events, stage diagnostics, and bounded logs. Exit 23 means orchestration could not reliably execute evidence; it is not itself proof of compatibility failure.

## Resources remain after failure

If `--keep-on-failure` was used, run the exact cleanup commands printed by Backline. Otherwise inspect the cleanup section and run ID. Backline only deletes exact registered paths or Docker resources labeled for that run.

## Output was truncated

Increase `artifacts.max_log_bytes_per_command` within the supported bound if required, or inspect project-owned external logs. Truncation is disclosed in structured artifacts.
