package dev.backline.reporting;

import dev.backline.core.api.dto.AssertionResultDto;
import dev.backline.core.api.dto.CheckResultDto;
import dev.backline.core.api.dto.ProjectDto;
import dev.backline.core.api.dto.ProjectSummaryDto;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.api.dto.RunDiffChangeType;
import dev.backline.core.api.dto.RunDiffEntry;
import dev.backline.core.api.dto.RunDto;
import dev.backline.core.check.CheckResultStatus;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/**
 * Builds the PRD-required Markdown sections from {@link ReportInputs}. Uses only in-memory data from the API.
 */
public final class DefaultMarkdownReportGenerator implements MarkdownReportGenerator {

    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    @Override
    public String generate(ReportInputs inputs) {
        Objects.requireNonNull(inputs, "inputs");
        RunDto run = Objects.requireNonNull(inputs.run(), "run");
        ProjectDto project = Objects.requireNonNull(inputs.project(), "project");
        List<CheckResultDto> results = inputs.results() == null ? List.of() : inputs.results();
        RunDiffDto diff = Objects.requireNonNull(inputs.diff(), "diff");
        Objects.requireNonNull(inputs.generatedAt(), "generatedAt");

        StringBuilder sb = new StringBuilder();
        sb.append("# Backline Run Report\n\n");

        sb.append(section(
                "Run summary",
                runSummaryBody(project, run)));

        sb.append(section("Check summary", checkSummaryTable(results)));

        String failed = failedChecksBody(results);
        if (!failed.isEmpty()) {
            sb.append(section("Failed checks", failed));
        }

        sb.append(section("Latency summary", latencySummaryBody(results)));

        sb.append(section("Diff against previous run", diffBody(diff)));

        sb.append(section("Project summary", projectSummaryBody(inputs.summary())));

        sb.append(section("Regression risk score", regressionRiskBody(results, diff)));

        sb.append(section(
                "Known limitations",
                "Backline is a local-first regression ledger: single-tenant (no auth), JSONPath assertions "
                        + "limited to deterministic single-operator rules, response previews bounded (~4096 bytes), "
                        + "and worker retries apply to runtime errors only (not failed assertions). "
                        + "Full list: [docs/known-limitations.md](docs/known-limitations.md) in the repository."));

        sb.append("\n**Generated at (UTC)**: ")
                .append(ISO_UTC.format(inputs.generatedAt()))
                .append('\n');

        return sb.toString();
    }

    private static String runSummaryBody(ProjectDto project, RunDto run) {
        StringBuilder b = new StringBuilder();
        b.append("- **Project**: ")
                .append(project.name())
                .append(" (`")
                .append(project.slug())
                .append("`)\n");
        b.append("- **Environment**: ").append(run.environment()).append('\n');
        b.append("- **Run ID**: ").append(run.id()).append('\n');
        b.append("- **Status**: ").append(run.status()).append('\n');
        b.append("- **Started**: ").append(formatInstant(run.startedAt())).append('\n');
        b.append("- **Finished**: ").append(formatInstant(run.finishedAt())).append('\n');
        b.append("- **Attempt count**: ").append(run.attemptCount()).append('\n');
        return b.toString();
    }

    private static String formatInstant(java.time.Instant instant) {
        return instant == null ? "—" : ISO_UTC.format(instant);
    }

    private static String checkSummaryTable(List<CheckResultDto> results) {
        long total = results.size();
        long passed = results.stream().filter(r -> r.status() == CheckResultStatus.PASSED).count();
        long failed = results.stream().filter(r -> r.status() == CheckResultStatus.FAILED).count();
        long errored = results.stream().filter(r -> r.status() == CheckResultStatus.ERROR).count();
        long skipped = results.stream().filter(r -> r.status() == CheckResultStatus.SKIPPED).count();
        List<String> headers = List.of("Metric", "Count");
        List<List<String>> rows =
                List.of(List.of("Total", Long.toString(total)), List.of("Passed", Long.toString(passed)),
                        List.of("Failed", Long.toString(failed)), List.of("Errored", Long.toString(errored)),
                        List.of("Skipped", Long.toString(skipped)));
        return table(headers, rows);
    }

    private static String failedChecksBody(List<CheckResultDto> results) {
        List<CheckResultDto> failed = results.stream()
                .filter(r -> r.status() == CheckResultStatus.FAILED || r.status() == CheckResultStatus.ERROR)
                .toList();
        if (failed.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (CheckResultDto r : failed) {
            b.append("### `").append(r.checkKey()).append("` — ").append(safe(r.checkName())).append("\n\n");
            b.append("- **Result status**: ").append(r.status()).append('\n');
            b.append("- **Expected outcome**: PASSED (check definition must be satisfied)\n");
            b.append("- **HTTP status**: ").append(r.actualStatus() == null ? "—" : r.actualStatus()).append('\n');
            b.append("- **Latency (ms)**: ").append(r.latencyMs() == null ? "—" : r.latencyMs()).append('\n');
            b.append("- **Error code**: ").append(r.errorCode() == null ? "—" : r.errorCode()).append('\n');
            b.append("- **Error message**: ").append(r.errorMessage() == null ? "—" : r.errorMessage()).append('\n');
            b.append(failedAssertionsBody(r.assertions()));
            b.append('\n');
        }
        return b.toString();
    }

    /**
     * Renders the assertions that did not pass for a failed check. Assertion outcomes are the primary
     * regression-debugging signal, so surfacing them keeps the report as informative as the API data
     * already fetched by the CLI.
     */
    private static String failedAssertionsBody(List<AssertionResultDto> assertions) {
        if (assertions == null || assertions.isEmpty()) {
            return "";
        }
        List<AssertionResultDto> failed = assertions.stream().filter(a -> !a.passed()).toList();
        if (failed.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        b.append("- **Failed assertions**:\n");
        for (AssertionResultDto a : failed) {
            b.append("  - `").append(safe(a.path())).append("`");
            if (a.expectedEquals() != null) {
                b.append(" expected `").append(a.expectedEquals()).append('`');
            } else if (a.expectedExists() != null) {
                b.append(" expected exists=`").append(a.expectedExists()).append('`');
            }
            b.append(", actual `").append(a.actual() == null ? "—" : a.actual()).append('`');
            if (a.message() != null && !a.message().isBlank()) {
                b.append(" (").append(a.message()).append(')');
            }
            b.append('\n');
        }
        return b.toString();
    }

    private static String latencySummaryBody(List<CheckResultDto> results) {
        List<CheckResultDto> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.comparingLong(
                        (CheckResultDto r) -> r.latencyMs() == null ? Long.MIN_VALUE : r.latencyMs())
                .reversed());
        List<String> headers = List.of("Check key", "Latency (ms)");
        List<List<String>> rows = new ArrayList<>();
        for (CheckResultDto r : sorted) {
            rows.add(List.of(r.checkKey(), r.latencyMs() == null ? "—" : Long.toString(r.latencyMs())));
        }
        String table = table(headers, rows);
        if (sorted.isEmpty()) {
            return table + "\n_No check results._\n";
        }
        CheckResultDto slowest = sorted.getFirst();
        Long lat = slowest.latencyMs();
        String slowLine = lat == null
                ? "\n**Slowest check**: `" + slowest.checkKey() + "` — latency not recorded.\n"
                : "\n**Slowest check**: `" + slowest.checkKey() + "` at **" + lat + " ms**.\n";
        return table + slowLine;
    }

    private static String diffBody(RunDiffDto diff) {
        if (diff.previousRunId() == null) {
            return "No previous completed run.\n";
        }
        List<RunDiffEntry> entries = diff.entries() == null ? List.of() : diff.entries();
        if (entries.isEmpty()) {
            return "Previous run: `" + diff.previousRunId() + "` — no diff entries.\n";
        }
        EnumMap<RunDiffChangeType, List<RunDiffEntry>> grouped = new EnumMap<>(RunDiffChangeType.class);
        for (RunDiffEntry e : entries) {
            grouped.computeIfAbsent(e.changeType(), k -> new ArrayList<>()).add(e);
        }
        StringBuilder b = new StringBuilder();
        b.append("Compared to previous run `").append(diff.previousRunId()).append("`.\n\n");
        for (RunDiffChangeType type : RunDiffChangeType.values()) {
            List<RunDiffEntry> list = grouped.get(type);
            if (list == null || list.isEmpty()) {
                continue;
            }
            b.append("#### ").append(type.name().replace('_', ' ')).append("\n\n");
            for (RunDiffEntry e : list) {
                b.append("- **").append(e.checkKey()).append("**");
                if (e.checkName() != null) {
                    b.append(" (").append(e.checkName()).append(")");
                }
                switch (type) {
                    case NEWLY_FAILING, NEWLY_PASSING, STILL_FAILING, STILL_PASSING -> b.append(": previous `")
                            .append(e.previousStatus())
                            .append("` → current `")
                            .append(e.currentStatus())
                            .append('`');
                    case STATUS_CODE_CHANGED -> b.append(": HTTP ")
                            .append(e.previousActualStatus())
                            .append(" → ")
                            .append(e.currentActualStatus());
                    case LATENCY_CHANGED -> {
                        Long p = e.previousLatencyMs();
                        Long c = e.currentLatencyMs();
                        long prev = p == null ? 0L : p;
                        long cur = c == null ? 0L : c;
                        long delta = cur - prev;
                        b.append(": latency ")
                                .append(p)
                                .append(" ms → ")
                                .append(c)
                                .append(" ms (Δ ")
                                .append(delta >= 0 ? "+" : "")
                                .append(delta)
                                .append(" ms)");
                    }
                    case NEWLY_ADDED -> b.append(": newly added in current run");
                    case REMOVED -> b.append(": removed since previous run");
                    case ASSERTION_CHANGED -> b.append(": assertion outcome changed");
                }
                b.append('\n');
            }
            b.append('\n');
        }
        return b.toString();
    }

    private static String projectSummaryBody(ProjectSummaryDto summary) {
        if (summary == null) {
            return "_No project summary provided._\n";
        }
        StringBuilder b = new StringBuilder();
        b.append("- **Total runs**: ").append(summary.totalRuns()).append('\n');
        b.append("- **Passed runs**: ").append(summary.passedRuns()).append('\n');
        b.append("- **Failed runs**: ").append(summary.failedRuns()).append('\n');
        b.append("- **Errored runs**: ").append(summary.erroredRuns()).append('\n');
        RunDto last = summary.lastRun();
        if (last == null) {
            b.append("- **Last run**: —\n");
        } else {
            b.append("- **Last run**: `")
                    .append(last.id())
                    .append("` — ")
                    .append(last.status());
            if (last.finishedAt() != null) {
                b.append(" (finished ").append(ISO_UTC.format(last.finishedAt())).append(')');
            }
            b.append('\n');
        }
        return b.toString();
    }

    private static String section(String title, String body) {
        return "## " + title + "\n\n" + body + "\n";
    }

    private static String regressionRiskBody(List<CheckResultDto> results, RunDiffDto diff) {
        int risk = 0;
        for (CheckResultDto result : results) {
            if (result.status() == CheckResultStatus.ERROR) {
                risk += 3;
            } else if (result.status() == CheckResultStatus.FAILED) {
                risk += 2;
            }
        }
        int diffSize = diff.entries() == null ? 0 : diff.entries().size();
        risk += diffSize;
        return "- **Risk score**: " + risk + "\n"
                + "- Formula: failed*2 + errored*3 + diffEntryCount\n";
    }

    private static String table(List<String> headers, List<List<String>> rows) {
        StringBuilder b = new StringBuilder();
        b.append("| ");
        b.append(String.join(" | ", headers));
        b.append(" |\n| ");
        b.append(String.join(" | ", Collections.nCopies(headers.size(), "---")));
        b.append(" |\n");
        for (List<String> row : rows) {
            b.append("| ");
            b.append(String.join(" | ", row));
            b.append(" |\n");
        }
        return b.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
