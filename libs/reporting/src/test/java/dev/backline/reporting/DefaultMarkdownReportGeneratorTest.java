package dev.backline.reporting;

import dev.backline.core.api.dto.AssertionResultDto;
import dev.backline.core.api.dto.CheckResultDto;
import dev.backline.core.api.dto.ProjectDto;
import dev.backline.core.api.dto.ProjectSummaryDto;
import dev.backline.core.api.dto.RunDiffChangeType;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.api.dto.RunDiffEntry;
import dev.backline.core.api.dto.RunDto;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.run.RunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMarkdownReportGeneratorTest {

    private static final Instant T0 = Instant.parse("2024-01-02T12:00:00Z");
    private static final Instant T1 = Instant.parse("2024-01-02T12:00:05Z");
    private static final Instant T2 = Instant.parse("2024-01-02T12:00:10Z");

    @Test
    void generatesRequiredSections_andNoPreviousRunMessage() {
        String runId = UUID.randomUUID().toString();
        String projectId = UUID.randomUUID().toString();
        RunDto run = new RunDto(
                runId,
                projectId,
                "local",
                RunStatus.FAILED,
                "abc123",
                "cli",
                null,
                T0,
                T1,
                T2,
                1);
        ProjectDto project = new ProjectDto(projectId, "sample-api", "Sample API", T0, T0);
        List<CheckResultDto> results = List.of(
                result("c1", "Health", CheckResultStatus.PASSED, 200, 40L),
                result("c2", "Broken", CheckResultStatus.FAILED, 500, 120L));
        RunDiffDto diff = new RunDiffDto(runId, null, List.of());
        ProjectSummaryDto summary =
                new ProjectSummaryDto(project, 5, 2, 2, 1, run);
        ReportInputs inputs = new ReportInputs(run, project, results, diff, summary, Instant.parse("2024-01-02T15:00:00Z"));

        String md = new DefaultMarkdownReportGenerator().generate(inputs);

        assertThat(md)
                .contains("# Backline Run Report")
                .contains("## Run summary")
                .contains("## Check summary")
                .contains("## Latency summary")
                .contains("## Diff against previous run")
                .contains("No previous completed run.")
                .contains("## Project summary")
                .contains("## Known limitations")
                .contains("docs/known-limitations.md")
                .contains("**Generated at (UTC)**:")
                .contains(runId)
                .contains("FAILED")
                .contains("`sample-api`")
                .contains("## Failed checks")
                .contains("`c2`")
                .contains("Broken");
    }

    @Test
    void failedCheck_rendersFailingAssertions() {
        String runId = UUID.randomUUID().toString();
        RunDto run = new RunDto(runId, "p1", "local", RunStatus.FAILED, "h", "cli", null, T0, T1, T2, 1);
        ProjectDto project = new ProjectDto("p1", "demo", "Demo", T0, T0);
        CheckResultDto failed = new CheckResultDto(
                UUID.randomUUID().toString(),
                runId,
                null,
                "get-user",
                "Fetch user",
                CheckResultStatus.FAILED,
                200,
                42L,
                "ASSERTION_FAILED",
                "One or more assertions failed",
                null,
                List.of(
                        new AssertionResultDto("$.id", 1, null, 2, false, "Value mismatch at $.id"),
                        new AssertionResultDto("$.email", null, true, "a@b.c", true, null)),
                T2);
        RunDiffDto diff = new RunDiffDto(runId, null, List.of());
        String md = new DefaultMarkdownReportGenerator().generate(
                new ReportInputs(run, project, List.of(failed), diff, null, Instant.parse("2024-01-02T15:00:00Z")));

        assertThat(md)
                .contains("**Failed assertions**")
                .contains("`$.id`")
                .contains("expected `1`")
                .contains("actual `2`")
                .contains("Value mismatch at $.id")
                .doesNotContain("`$.email`");
    }

    @Test
    void latencyTable_sortedDescending_andHighlightsSlowest() {
        RunDto run = new RunDto(
                "r1", "p1", "local", RunStatus.PASSED, "h", "cli", null, T0, T1, T2, 1);
        ProjectDto project = new ProjectDto("p1", "demo", "Demo", T0, T0);
        List<CheckResultDto> results = List.of(
                result("a-fast", "A", CheckResultStatus.PASSED, 200, 50L),
                result("z-slow", "Z", CheckResultStatus.PASSED, 200, 300L),
                result("m-mid", "M", CheckResultStatus.PASSED, 200, 100L));
        RunDiffDto diff = new RunDiffDto("r1", null, List.of());
        String md = new DefaultMarkdownReportGenerator()
                .generate(new ReportInputs(run, project, results, diff, null, Instant.parse("2024-01-02T15:00:00Z")));

        int iSlow = md.indexOf("| z-slow | 300 |");
        int iMid = md.indexOf("| m-mid | 100 |");
        int iFast = md.indexOf("| a-fast | 50 |");
        assertThat(iSlow).isGreaterThanOrEqualTo(0).isLessThan(iMid);
        assertThat(iMid).isLessThan(iFast);
        assertThat(md).contains("**Slowest check**: `z-slow`");
    }

    @Test
    void diffSection_listsEntriesGroupedByChangeType() {
        RunDto run = new RunDto(
                "r2", "p1", "local", RunStatus.FAILED, "h", "cli", null, T0, T1, T2, 1);
        ProjectDto project = new ProjectDto("p1", "demo", "Demo", T0, T0);
        List<CheckResultDto> results = List.of();
        List<RunDiffEntry> entries = List.of(
                new RunDiffEntry(
                        "x",
                        "X",
                        RunDiffChangeType.NEWLY_FAILING,
                        CheckResultStatus.PASSED,
                        CheckResultStatus.FAILED,
                        200,
                        500,
                        10L,
                        20L),
                new RunDiffEntry(
                        "y",
                        "Y",
                        RunDiffChangeType.STATUS_CODE_CHANGED,
                        null,
                        null,
                        200,
                        404,
                        null,
                        null),
                new RunDiffEntry(
                        "z",
                        "Z",
                        RunDiffChangeType.LATENCY_CHANGED,
                        CheckResultStatus.PASSED,
                        CheckResultStatus.PASSED,
                        200,
                        200,
                        100L,
                        250L));
        RunDiffDto diff = new RunDiffDto("r2", "r-prev", entries);
        String md = new DefaultMarkdownReportGenerator()
                .generate(new ReportInputs(run, project, results, diff, null, Instant.parse("2024-01-02T15:00:00Z")));

        assertThat(md)
                .contains("Compared to previous run `r-prev`")
                .contains("NEWLY FAILING")
                .contains("`PASSED` → current `FAILED`")
                .contains("STATUS CODE CHANGED")
                .contains("HTTP 200 → 404")
                .contains("LATENCY CHANGED")
                .contains("Δ +150 ms");
    }

    private static CheckResultDto result(
            String key, String name, CheckResultStatus status, int http, Long latency) {
        return new CheckResultDto(
                UUID.randomUUID().toString(),
                "run",
                null,
                key,
                name,
                status,
                http,
                latency,
                status == CheckResultStatus.FAILED ? "ASSERTION" : null,
                status == CheckResultStatus.FAILED ? "expected 200" : null,
                null,
                List.of(),
                T2);
    }
}
