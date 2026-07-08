package dev.backline.cli.commands;

import dev.backline.cli.client.ApiClientException;
import dev.backline.cli.client.BacklineApiClient;
import dev.backline.cli.policy.JunitPolicyReportWriter;
import dev.backline.cli.policy.PolicyEvaluation;
import dev.backline.cli.policy.RunPolicyEvaluator;
import dev.backline.cli.policy.RunPolicyProfile;
import dev.backline.cli.policy.RunPolicyProfileConverter;
import picocli.CommandLine;
import dev.backline.config.ConfigParseException;
import dev.backline.config.ConfigParser;
import dev.backline.config.model.BacklineConfig;
import dev.backline.config.model.RunPolicy;
import dev.backline.core.api.dto.CheckDefinitionDto;
import dev.backline.core.api.dto.CheckSyncRequest;
import dev.backline.core.api.dto.CreateProjectRequest;
import dev.backline.core.api.dto.CreateRunRequest;
import dev.backline.core.api.dto.DiffBaselineStrategy;
import dev.backline.core.api.dto.RunDiffDto;
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

    @Option(names = {"--enforce-policy"}, description = "Evaluate policy thresholds after run completion")
    private boolean enforcePolicy;

    @Option(
            names = {"--policy"},
            description = "Override config policy with preset: strict, warn-only")
    private String policyPreset;

    @Option(names = {"--junit-output"}, description = "Optional JUnit XML output path for policy enforcement")
    private Path junitOutput;

    @Option(
            names = {"--baseline"},
            description = "Diff baseline strategy used for policy evaluation: ${COMPLETION-CANDIDATES}",
            defaultValue = "PREVIOUS_COMPLETED")
    private DiffBaselineStrategy baseline;

    @Option(
            names = {"--baseline-run-id"},
            description = "Required when --baseline=FIXED_RUN")
    private UUID baselineRunId;

    @Override
    public Integer call() throws Exception {
        if (!noWait && timeoutSeconds <= 0) {
            System.err.println("--timeout-seconds must be greater than zero.");
            return 2;
        }
        if (enforcePolicy && baseline == DiffBaselineStrategy.FIXED_RUN && baselineRunId == null) {
            System.err.println("--baseline-run-id is required when --baseline=FIXED_RUN");
            return 2;
        }
        if (policyPreset != null) {
            try {
                RunPolicyProfile.fromCliValue(policyPreset);
            } catch (CommandLine.TypeConversionException e) {
                System.err.println(e.getMessage());
                return 2;
            }
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
        RunPolicy effectivePolicy = resolvePolicy(config.policy());
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
                    if (enforcePolicy) {
                        System.err.println("--enforce-policy requires a terminal run status; remove --no-wait.");
                        return 2;
                    }
                    return 0;
                }
                if (enforcePolicy && st.isTerminal()) {
                    PolicyEvaluation evaluation = evaluatePolicy(client, runId, effectivePolicy);
                    return policyAwareExit(runId, st, evaluation);
                }
                return exitForTerminal(st);
            }
            return waitForTerminal(client, runId, run.status(), enforcePolicy ? effectivePolicy : null);
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

    private int waitForTerminal(BacklineApiClient client, UUID runId, RunStatus initial, RunPolicy policy)
            throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(timeoutSeconds);
        RunStatus lastPrinted = null;
        RunStatus current = initial;
        while (true) {
            if (current != null && current != lastPrinted) {
                System.out.println("status: " + current);
                lastPrinted = current;
            }
            if (current != null && current.isTerminal()) {
                if (policy != null) {
                    PolicyEvaluation evaluation = evaluatePolicy(client, runId, policy);
                    return policyAwareExit(runId, current, evaluation);
                }
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

    private int policyAwareExit(UUID runId, RunStatus terminalStatus, PolicyEvaluation evaluation)
            throws java.io.IOException {
        if (junitOutput != null) {
            JunitPolicyReportWriter.write(junitOutput, runId.toString(), evaluation);
        }
        if (!evaluation.passed()) {
            System.err.println("Policy violations:");
            for (String violation : evaluation.violations()) {
                System.err.println("- " + violation);
            }
            return 5;
        }
        return exitForTerminal(terminalStatus);
    }

    private PolicyEvaluation evaluatePolicy(BacklineApiClient client, UUID runId, RunPolicy configuredPolicy)
            throws java.io.IOException, InterruptedException {
        RunPolicy policy = configuredPolicy == null ? RunPolicyEvaluator.DEFAULT_POLICY : configuredPolicy;
        var results = client.getRunResults(runId);
        RunDiffDto diff = client.getRunDiff(runId, baseline, baselineRunId);
        PolicyEvaluation evaluation = RunPolicyEvaluator.evaluate(policy, results, diff);
        System.out.println("policy: newly_failing=" + evaluation.newlyFailingCount()
                + ", errored_checks=" + evaluation.erroredChecksCount()
                + ", max_latency_regression_ms=" + evaluation.maxLatencyRegressionMs());
        return evaluation;
    }

    private RunPolicy resolvePolicy(RunPolicy configPolicy) {
        if (policyPreset != null) {
            return RunPolicyProfile.fromCliValue(policyPreset).toPolicy();
        }
        return configPolicy;
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
