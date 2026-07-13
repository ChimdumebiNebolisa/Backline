# Demo capture source

`failed-run-diff.webp` and `markdown-report.webp` render unmodified CLI and Markdown text from the successful `e2e-demo` job in [GitHub Actions run 29216457678](https://github.com/ChimdumebiNebolisa/Backline/actions/runs/29216457678). That job executes `scripts/ci-e2e-demo.sh`, the repository's Docker-backed end-to-end demo.

The source job ran these commands:

```bash
docker run -d --rm -e POSTGRES_DB=backline -e POSTGRES_USER=backline -e POSTGRES_PASSWORD=backline -p 5432:5432 postgres:16-alpine
./gradlew :apps:cli:installDist -x test --no-daemon -q
backline doctor
(cd examples/sample-api && backline run --timeout-seconds 120)
backline diff "$RUN_ID"
backline report "$RUN_ID" -o "$REPORT_MD"
```

The captured run intentionally fails `broken-endpoint` with HTTP 500. The screenshots contain no secrets or personal filesystem paths. Only the CI frame, command prompts, sizing, and WebP encoding were added; CLI and report values were not fabricated. The report frame shows its title, run summary, and check summary without altering the generated Markdown.
