package dev.backline.cli.commands;

import dev.backline.cli.Backline;
import dev.backline.cli.client.ApiClientException;
import dev.backline.cli.client.BacklineApiClient;
import dev.backline.core.api.dto.CheckResultDto;
import dev.backline.core.api.dto.RunDto;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.run.RunStatus;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/** Prints a terse summary for a single run. */
@Command(mixinStandardHelpOptions = true, name = "status", description = "Show status for a run.")
public class StatusCommand implements Callable<Integer> {

    @ParentCommand
    private Backline parent;

    @Parameters(index = "0", description = "Run id", arity = "1")
    private UUID runId;

    @Override
    public Integer call() throws Exception {
        BacklineApiClient client = new BacklineApiClient(parent.apiUrl());
        RunDto run;
        List<CheckResultDto> results;
        try {
            run = client.getRun(runId);
            results = client.getRunResults(runId);
        } catch (ApiClientException e) {
            return CliApiErrors.print(parent.apiUrl(), e);
        } catch (InterruptedException e) {
            return CliApiErrors.printInterrupted();
        } catch (IOException e) {
            return CliApiErrors.print(parent.apiUrl(), e);
        }
        Map<CheckResultStatus, Long> counts = new EnumMap<>(CheckResultStatus.class);
        for (var r : results) {
            counts.merge(r.status(), 1L, Long::sum);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("run ")
                .append(run.id())
                .append(" status ")
                .append(run.status())
                .append(" env ")
                .append(run.environment());
        System.out.println(sb);
        System.out.println(
                "results: PASSED=" + counts.getOrDefault(CheckResultStatus.PASSED, 0L)
                        + " FAILED=" + counts.getOrDefault(CheckResultStatus.FAILED, 0L)
                        + " ERROR=" + counts.getOrDefault(CheckResultStatus.ERROR, 0L)
                        + " SKIPPED=" + counts.getOrDefault(CheckResultStatus.SKIPPED, 0L));
        if (run.status().isTerminal()) {
            return switch (run.status()) {
                case PASSED -> 0;
                case FAILED -> 1;
                case ERROR -> 2;
                default -> 0;
            };
        }
        return 0;
    }
}
