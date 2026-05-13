package dev.backline.cli;

import dev.backline.cli.commands.DiffCommand;
import dev.backline.cli.commands.DoctorCommand;
import dev.backline.cli.commands.HistoryCommand;
import dev.backline.cli.commands.InitCommand;
import dev.backline.cli.commands.ReportCommand;
import dev.backline.cli.commands.RunCommand;
import dev.backline.cli.commands.SampleCommand;
import dev.backline.cli.commands.StatusCommand;
import dev.backline.cli.commands.WorkerCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

/**
 * Root Picocli entrypoint for Backline CLI commands. Shared {@code --api-url} defaults from {@code BACKLINE_API_URL}
 * with fallback {@code http://localhost:8080}.
 */
@Command(
        name = "backline",
        mixinStandardHelpOptions = true,
        version = "backline 0.1",
        subcommands = {
            InitCommand.class,
            SampleCommand.class,
            RunCommand.class,
            StatusCommand.class,
            HistoryCommand.class,
            DiffCommand.class,
            ReportCommand.class,
            WorkerCommand.class,
            DoctorCommand.class
        })
public class Backline {

    @Option(
            names = {"--api-url"},
            description = "Base URL for the Backline API (env BACKLINE_API_URL)",
            defaultValue = "${env:BACKLINE_API_URL:-http://localhost:8080}",
            scope = ScopeType.INHERIT)
    private String apiUrl;

    public String apiUrl() {
        return apiUrl;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Backline()).execute(args);
        System.exit(exitCode);
    }
}
