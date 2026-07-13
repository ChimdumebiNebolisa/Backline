# Known limitations

Backline is intentionally scoped as a **local-first regression ledger**, not a full API platform. Current limitations include:

- **Single-tenant**: there is no authentication, authorization, or per-user isolation.
- **No interactive API client**: Backline is not a Postman replacement; it does not focus on ad-hoc request building or GUI workflows.
- **JSONPath assertions**: supported operators are intentionally limited to deterministic single-operator rules (`equals`, `exists`, `not_equals`, `contains`, `regex`, `gt`, `gte`, `lt`, `lte`).
- **Response previews** are bounded (for example, **4096 bytes**) to avoid storing large payloads by default.
- **Worker retries** apply to **runtime / worker errors** only, not to failed HTTP assertions (a failed assertion is a failed check, not a retryable infrastructure fault).
- **Markdown reports** and optional **JSON report artifacts** (`backline report --json-output`).
- **Schema migrations** are owned by the **API** process (Flyway on startup). The worker assumes the database schema matches the migrations shipped with the API.
- **Sample API** ships **intentional failures** and odd shapes for demos; do not treat it as a production service.
- **Diff baseline**: comparison uses the most recent **completed** run (status **PASSED** or **FAILED**) for the same project and environment that was **queued before the current run**; **CANCELLED** and **ERROR** runs are skipped as previous baselines. Status-code and assertion changes are reported even when a check stays failing, so a shift such as `500 → 404` is not hidden behind a generic still-failing label.
- **No load testing**, fuzzing, or contract testing against arbitrary OpenAPI documents in this build.
- **No cloud sync**, SaaS hosting workflow, or team permission model.

See also the root [README.md](../README.md) troubleshooting section for operational issues.
