# CI security

Backline builds images and executes hooks and workloads from the candidate revision. Treat that revision as trusted code.

- Never use `pull_request_target` to run Backline against pull-request code.
- Do not expose secrets, package credentials, cloud credentials, or privileged runners to untrusted forks.
- Run config/unit validation on forks without Docker execution.
- Require maintainer approval before a secret-free Docker E2E run of external contributions.
- Keep the Docker daemon local unless an operator explicitly chooses `--allow-remote-docker` for a trusted disposable endpoint.
- Always upload redacted artifacts, but do not assume best-effort redaction can make hostile output safe.

Docker daemon access commonly grants host-equivalent power. Backline's Compose policy reduces accidental host/resource access; it is not a sandbox against malicious project code.
