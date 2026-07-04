package dev.backline.cli.commands;

import dev.backline.cli.Backline;
import dev.backline.cli.client.ApiClientException;
import dev.backline.cli.client.BacklineApiClient;
import dev.backline.core.api.dto.CheckResultDto;
import dev.backline.core.api.dto.ProjectSummaryDto;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.api.dto.RunDto;
import dev.backline.reporting.DefaultMarkdownReportGenerator;
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
        String md = generator.generate(new ReportInputs(
                run, summary.project(), results, diff, summary, Instant.now()));
        Path out = Path.of(output == null ? ("backline-report-" + runId + ".md") : output).toAbsolutePath().normalize();
        Path parent = out.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(out, md);
        System.out.println(out);
        return 0;
    }
}
