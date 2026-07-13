# Demo output source

The terminal and Markdown evidence rendered as text on the landing page comes from the successful `e2e-demo` job in [GitHub Actions run 29216457678](https://github.com/ChimdumebiNebolisa/Backline/actions/runs/29216457678). That job executes `scripts/ci-e2e-demo.sh`, the repository's Docker-backed end-to-end demo.

The source job ran these commands:

```bash
docker run -d --rm -e POSTGRES_DB=backline -e POSTGRES_USER=backline -e POSTGRES_PASSWORD=backline -p 5432:5432 postgres:16-alpine
./gradlew :apps:cli:installDist -x test --no-daemon -q
backline doctor
(cd examples/sample-api && backline run --timeout-seconds 120)
backline diff "$RUN_ID"
backline report "$RUN_ID" -o "$REPORT_MD"
```

The rendered run intentionally fails `broken-endpoint` with HTTP 500. The excerpts contain no secrets or personal filesystem paths. CLI and report values are copied from the CI artifact without fabrication.
