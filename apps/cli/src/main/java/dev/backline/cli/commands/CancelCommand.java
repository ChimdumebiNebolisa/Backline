package dev.backline.cli.commands;

import dev.backline.cli.Backline;
import dev.backline.cli.client.ApiClientException;
import dev.backline.cli.client.BacklineApiClient;
import dev.backline.core.api.dto.RunDto;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Cancels a queued or running run through the API. Terminal runs cannot be cancelled; the API
 * answers 409 and the CLI prints an actionable message with exit code 1.
 */
@Command(
        mixinStandardHelpOptions = true,
        name = "cancel",
        description = "Cancel a queued or running run.")
public class CancelCommand implements Callable<Integer> {

    @ParentCommand
    private Backline parent;

    @Parameters(index = "0", description = "Run id", arity = "1")
    private UUID runId;

    @Override
    public Integer call() {
        BacklineApiClient client = new BacklineApiClient(parent.apiUrl());
        try {
            RunDto run = client.cancelRun(runId);
            System.out.println("cancelled run " + run.id() + " status " + run.status());
            return 0;
        } catch (ApiClientException e) {
            if (e.httpStatus() == 409) {
                System.err.println("Cannot cancel run " + runId + ": " + e.getMessage()
                        + ". Only QUEUED or RUNNING runs can be cancelled.");
                return 1;
            }
            return CliApiErrors.print(parent.apiUrl(), e);
        } catch (InterruptedException e) {
            return CliApiErrors.printInterrupted();
        } catch (IOException e) {
            return CliApiErrors.print(parent.apiUrl(), e);
        }
    }
}
