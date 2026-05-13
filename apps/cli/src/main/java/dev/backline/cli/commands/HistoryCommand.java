package dev.backline.cli.commands;

import dev.backline.cli.Backline;
import dev.backline.cli.client.BacklineApiClient;
import dev.backline.cli.client.RunListQuery;
import dev.backline.cli.output.OutputPrinter;
import dev.backline.config.ConfigParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** Lists recent runs with optional filters. */
@Command(mixinStandardHelpOptions = true, name = "history", description = "List runs from the API.")
public class HistoryCommand implements Callable<Integer> {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT;

    @ParentCommand
    private Backline parent;

    @Option(names = {"-p", "--project"}, description = "Project slug (defaults from backline.yml if present)")
    private String project;

    @Option(names = {"-e", "--environment"}, description = "Environment filter (defaults from backline.yml if present)")
    private String environment;

    @Option(names = {"--status"}, description = "Run status filter")
    private String status;

    @Option(names = {"--limit"}, description = "Page limit", defaultValue = "25")
    private int limit;

    @Option(names = {"--offset"}, description = "Page offset", defaultValue = "0")
    private int offset;

    @Override
    public Integer call() throws Exception {
        String projectSlug = project;
        String env = environment;
        if (projectSlug == null || env == null) {
            Path yml = Path.of("backline.yml");
            if (Files.isRegularFile(yml)) {
                var cfg = new ConfigParser().parse(yml.toAbsolutePath().normalize());
                if (projectSlug == null) {
                    projectSlug = cfg.project();
                }
                if (env == null) {
                    env = cfg.environment();
                }
            }
        }
        BacklineApiClient client = new BacklineApiClient(parent.apiUrl());
        var runs = client.listRuns(new RunListQuery(projectSlug, env, status, limit, offset));
        OutputPrinter out = new OutputPrinter();
        List<List<String>> rows = new ArrayList<>();
        for (var r : runs) {
            rows.add(List.of(
                    r.id(),
                    r.status().name(),
                    r.environment(),
                    r.queuedAt() == null ? "" : TS.format(r.queuedAt()),
                    r.finishedAt() == null ? "" : TS.format(r.finishedAt())));
        }
        out.printTable(List.of("RUN_ID", "STATUS", "ENV", "QUEUED_AT", "FINISHED_AT"), rows);
        return 0;
    }
}
