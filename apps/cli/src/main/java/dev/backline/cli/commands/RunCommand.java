package dev.backline.cli.commands;

import dev.backline.cli.client.ApiClientException;
import dev.backline.cli.client.BacklineApiClient;
import dev.backline.config.ConfigParseException;
import dev.backline.config.ConfigParser;
import dev.backline.config.model.BacklineConfig;
import dev.backline.core.api.dto.CheckDefinitionDto;
import dev.backline.core.api.dto.CheckSyncRequest;
import dev.backline.core.api.dto.CreateProjectRequest;
import dev.backline.core.api.dto.CreateRunRequest;
import dev.backline.core.error.ErrorCode;
import dev.backline.core.run.RunStatus;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import dev.backline.cli.Backline;

/**
 * Parses {@code backline.yml}, syncs checks, submits a run, and waits for completion unless {@code --no-wait}
 * is set.
 */
@Command(mixinStandardHelpOptions = true, name = "run", description = "Submit a regression run using backline.yml.")
public class RunCommand implements Callable<Integer> {

    @ParentCommand
    private Backline parent;

    @Option(names = {"-f", "--file"}, description = "Path to backline.yml", defaultValue = "backline.yml")
    private Path file;

    @Option(names = {"--no-wait"}, description = "Submit the run and return immediately")
    private boolean noWait;

    @Option(names = {"--timeout-seconds"}, description = "Max seconds to wait for a terminal status", defaultValue = "60")
    private int timeoutSeconds;

    @Option(names = {"--source"}, description = "Run source label stored with the run", defaultValue = "cli")
    private String source;

    @Option(names = {"--idempotency-key"}, description = "Optional idempotency key forwarded to the API")
    private String idempotencyKey;

    @Override
    public Integer call() throws Exception {
        if (!noWait && timeoutSeconds <= 0) {
            System.err.println("--timeout-seconds must be greater than zero.");
            return 2;
        }

        ConfigParser parser = new ConfigParser();
        BacklineConfig config;
        try {
            config = parser.parse(file.toAbsolutePath().normalize());
        } catch (ConfigParseException e) {
            System.err.println(e.getMessage());
            return 2;
        }
        String configHash = parser.canonicalConfigHash(config);
        BacklineApiClient client = new BacklineApiClient(parent.apiUrl());
        try {
            try {
                client.createProject(new CreateProjectRequest(config.project(), config.project()));
            } catch (ApiClientException ex) {
                if (ex.httpStatus() != 409 || ex.code() != ErrorCode.CONFLICT) {
                    throw ex;
                }
            }
            List<CheckDefinitionDto> checks = config.checks().stream()
                    .map(c -> new CheckDefinitionDto(
                            c.key(),
                            c.name(),
                            c.method(),
                            c.url(),
                            c.expectedStatus(),
                            c.maxLatencyMs(),
                            c.assertions()))
                    .toList();
            client.syncChecks(new CheckSyncRequest(config.project(), config.project(), checks));
            var run = client.submitRun(new CreateRunRequest(
                    config.project(), config.environment(), configHash, idempotencyKey, source));
            UUID runId = UUID.fromString(run.id());
            System.out.println("RUN_ID: " + runId);
            if (noWait) {
                RunStatus st = run.status();
                if (st == RunStatus.QUEUED) {
                    return 0;
                }
                return exitForTerminal(st);
            }
            return waitForTerminal(client, runId, run.status());
        } catch (ApiClientException e) {
            System.err.println("API error (" + e.httpStatus() + "): " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted");
            return 1;
        } catch (java.io.IOException e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            System.err.println("Cannot reach API at " + parent.apiUrl() + ": " + detail);
            return 1;
        }
    }

    private int waitForTerminal(BacklineApiClient client, UUID runId, RunStatus initial) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(timeoutSeconds);
        RunStatus lastPrinted = null;
        RunStatus current = initial;
        while (true) {
            if (current != null && current != lastPrinted) {
                System.out.println("status: " + current);
                lastPrinted = current;
            }
            if (current != null && current.isTerminal()) {
                return exitForTerminal(current);
            }
            if (System.nanoTime() > deadline) {
                System.err.println("Timed out waiting for run " + runId);
                return 4;
            }
            Thread.sleep(500);
            current = client.getRun(runId).status();
        }
    }

    private static int exitForTerminal(RunStatus status) {
        if (status == RunStatus.PASSED) {
            return 0;
        }
        if (status == RunStatus.FAILED) {
            return 1;
        }
        if (status == RunStatus.ERROR) {
            return 2;
        }
        if (status == RunStatus.CANCELLED) {
            return 3;
        }
        return 0;
    }
}
