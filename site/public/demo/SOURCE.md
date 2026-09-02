# Demo output source

The CLI excerpts on the landing page were observed locally on 2026-09-02 by running the deterministic `safe`, `mixed-failure`, and `rollback-failure` Docker fixtures. The harness asserted exact verdicts, controls, rollback mode, reason codes, exit status, schemas, loopback endpoints, redaction, and cleanup before the excerpts were used.

The source fixtures and their assertions live under `examples/rollout-demo`. Dynamic run IDs, commit SHAs, timestamps, and absolute paths are omitted from the landing page because each execution generates new values.
