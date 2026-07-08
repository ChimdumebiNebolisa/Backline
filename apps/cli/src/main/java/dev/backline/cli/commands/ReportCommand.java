package dev.backline.cli.commands;

import dev.backline.cli.Backline;
import dev.backline.cli.client.ApiClientException;
import dev.backline.cli.client.BacklineApiClient;
import dev.backline.core.api.dto.CheckResultDto;
import dev.backline.core.api.dto.ProjectSummaryDto;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.api.dto.RunDto;
import dev.backline.reporting.DefaultMarkdownReportGenerator;
import dev.backline.reporting.DefaultJsonReportGenerator;
import dev.backline.reporting.MarkdownReportGenerator;
import dev.backline.reporting.ReportInputs;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Generates a Markdown report for a run using API data via {@link MarkdownReportGenerator}.
 */
@Command(mixinStandardHelpOptions = true, name = "report", description = "Generate a Markdown report for a run.")
public class ReportCommand implements Callable<Integer> {

    @ParentCommand
    private Backline parent;

    @Parameters(index = "0", description = "Run id", arity = "1")
    private UUID runId;

    @Option(names = {"-o", "--output"}, description = "Output Markdown path")
    private String output;

    @Option(names = {"--json-output"}, description = "Optional JSON report output path")
    private String jsonOutput;

    @Override
    public Integer call() throws Exception {
        BacklineApiClient client = new BacklineApiClient(parent.apiUrl());
        RunDto run;
        List<CheckResultDto> results;
        RunDiffDto diff;
        ProjectSummaryDto summary;
        try {
            run = client.getRun(runId);
            results = client.getRunResults(runId);
            diff = client.getRunDiff(runId);
            summary = client.getProjectSummary(UUID.fromString(run.projectId()));
        } catch (ApiClientException e) {
            return CliApiErrors.print(parent.apiUrl(), e);
        } catch (InterruptedException e) {
            return CliApiErrors.printInterrupted();
        } catch (IOException e) {
            return CliApiErrors.print(parent.apiUrl(), e);
        }
        MarkdownReportGenerator generator = new DefaultMarkdownReportGenerator();
        ReportInputs reportInputs = new ReportInputs(run, summary.project(), results, diff, summary, Instant.now());
        String md = generator.generate(reportInputs);
        Path out = Path.of(output == null ? ("backline-report-" + runId + ".md") : output).toAbsolutePath().normalize();
        Path parent = out.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(out, md);
        System.out.println(out);
        if (jsonOutput != null && !jsonOutput.isBlank()) {
            String json = new DefaultJsonReportGenerator().generate(reportInputs);
            Path jsonPath = Path.of(jsonOutput).toAbsolutePath().normalize();
            Path jsonParent = jsonPath.getParent();
            if (jsonParent != null) {
                Files.createDirectories(jsonParent);
            }
            Files.writeString(jsonPath, json);
            System.out.println(jsonPath);
        }
        return 0;
    }
}
