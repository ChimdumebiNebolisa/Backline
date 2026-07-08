package dev.backline.cli.policy;

import dev.backline.config.model.RunPolicy;
import dev.backline.core.api.dto.CheckResultDto;
import dev.backline.core.api.dto.RunDiffChangeType;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.api.dto.RunDiffEntry;
import dev.backline.core.check.CheckResultStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates run diff/results against configurable thresholds used for CI gating.
 */
public final class RunPolicyEvaluator {

    public static final RunPolicy DEFAULT_POLICY = new RunPolicy(0, 0, null);

    private RunPolicyEvaluator() {}

    public static PolicyEvaluation evaluate(RunPolicy policy, List<CheckResultDto> results, RunDiffDto diff) {
        RunPolicy effective = policy == null ? DEFAULT_POLICY : policy;
        int newlyFailing = countNewlyFailing(diff);
        int erroredChecks = countErroredChecks(results);
        long maxLatencyRegression = maxLatencyRegressionMs(diff);

        List<String> violations = new ArrayList<>();
        if (effective.maxNewlyFailing() != null && newlyFailing > effective.maxNewlyFailing()) {
            violations.add("newly failing checks " + newlyFailing + " exceeds max_newly_failing " + effective.maxNewlyFailing());
        }
        if (effective.maxErroredChecks() != null && erroredChecks > effective.maxErroredChecks()) {
            violations.add("errored checks " + erroredChecks + " exceeds max_errored_checks " + effective.maxErroredChecks());
        }
        if (effective.maxLatencyRegressionMs() != null && maxLatencyRegression > effective.maxLatencyRegressionMs()) {
            violations.add("max latency regression " + maxLatencyRegression
                    + " ms exceeds max_latency_regression_ms " + effective.maxLatencyRegressionMs() + " ms");
        }
        return new PolicyEvaluation(
                violations.isEmpty(),
                newlyFailing,
                erroredChecks,
                maxLatencyRegression,
                List.copyOf(violations));
    }

    private static int countNewlyFailing(RunDiffDto diff) {
        if (diff == null || diff.entries() == null) {
            return 0;
        }
        int total = 0;
        for (RunDiffEntry entry : diff.entries()) {
            if (entry.changeType() == RunDiffChangeType.NEWLY_FAILING) {
                total++;
            }
        }
        return total;
    }

    private static int countErroredChecks(List<CheckResultDto> results) {
        if (results == null) {
            return 0;
        }
        int total = 0;
        for (CheckResultDto result : results) {
            if (result.status() == CheckResultStatus.ERROR) {
                total++;
            }
        }
        return total;
    }

    private static long maxLatencyRegressionMs(RunDiffDto diff) {
        if (diff == null || diff.entries() == null) {
            return 0L;
        }
        long max = 0L;
        for (RunDiffEntry entry : diff.entries()) {
            if (entry.changeType() != RunDiffChangeType.LATENCY_CHANGED) {
                continue;
            }
            Long prev = entry.previousLatencyMs();
            Long current = entry.currentLatencyMs();
            if (prev == null || current == null) {
                continue;
            }
            long delta = current - prev;
            if (delta > max) {
                max = delta;
            }
        }
        return max;
    }
}
