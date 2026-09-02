# CI integration

Run validation on ordinary pull requests, then allow `verify` only on trusted code with Docker access.

```yaml
- name: Verify rollout compatibility
  run: |
    backline verify \
      --artifact-dir build/backline/runs \
      --json-output build/backline/summary.json \
      --junit-output build/backline/junit.xml \
      --no-color

- name: Upload Backline evidence
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: backline-evidence
    path: build/backline/**
    if-no-files-found: error
```

Use the CI provider's JUnit upload/report action for `build/backline/junit.xml`. Preserve the whole run directory even when the command fails or is interrupted; partial events and bounded logs are diagnostic evidence.

The repository CI runs format, unit tests, vet, Linux race tests, schema checks, vulnerability and secret scans, and six Linux Docker E2E fixtures. The release workflow cross-builds Linux amd64/arm64, macOS amd64/arm64, and Windows amd64 archives with SHA-256 checksums.

Read [CI security](ci-security.md) before enabling Docker execution for pull requests.
