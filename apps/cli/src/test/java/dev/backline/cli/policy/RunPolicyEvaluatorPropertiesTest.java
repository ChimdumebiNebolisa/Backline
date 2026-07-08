package dev.backline.cli.policy;

import dev.backline.config.model.RunPolicy;
import dev.backline.core.api.dto.CheckResultDto;
import dev.backline.core.api.dto.RunDiffChangeType;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.api.dto.RunDiffEntry;
import dev.backline.core.check.CheckResultStatus;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunPolicyEvaluatorPropertiesTest {

    @Property
    void passesWhenNewlyFailingAtOrBelowThreshold(
            @ForAll @IntRange(min = 0, max = 20) int threshold,
            @ForAll @IntRange(min = 0, max = 20) int newlyFailingCount) {
        if (newlyFailingCount > threshold) {
            return;
        }
        RunPolicy policy = new RunPolicy(threshold, null, null);
        RunDiffDto diff = diffWithNewlyFailing(newlyFailingCount);

        PolicyEvaluation evaluation = RunPolicyEvaluator.evaluate(policy, List.of(), diff);

        assertThat(evaluation.passed()).isTrue();
        assertThat(evaluation.newlyFailingCount()).isEqualTo(newlyFailingCount);
    }

    @Property
    void failsWhenNewlyFailingExceedsThreshold(@ForAll @IntRange(min = 0, max = 20) int threshold) {
        int newlyFailingCount = threshold + 1;
        RunPolicy policy = new RunPolicy(threshold, null, null);
        RunDiffDto diff = diffWithNewlyFailing(newlyFailingCount);

        PolicyEvaluation evaluation = RunPolicyEvaluator.evaluate(policy, List.of(), diff);

        assertThat(evaluation.passed()).isFalse();
        assertThat(evaluation.violations()).anyMatch(v -> v.contains("max_newly_failing"));
    }

    @Property
    void passesWhenErroredChecksAtOrBelowThreshold(
            @ForAll @IntRange(min = 0, max = 20) int threshold,
            @ForAll @IntRange(min = 0, max = 20) int erroredCount) {
        if (erroredCount > threshold) {
            return;
        }
        RunPolicy policy = new RunPolicy(null, threshold, null);
        List<CheckResultDto> results = erroredResults(erroredCount);

        PolicyEvaluation evaluation = RunPolicyEvaluator.evaluate(policy, results, null);

        assertThat(evaluation.passed()).isTrue();
        assertThat(evaluation.erroredChecksCount()).isEqualTo(erroredCount);
    }

    @Property
    void failsWhenErroredChecksExceedThreshold(@ForAll @IntRange(min = 0, max = 20) int threshold) {
        int erroredCount = threshold + 1;
        RunPolicy policy = new RunPolicy(null, threshold, null);
        List<CheckResultDto> results = erroredResults(erroredCount);

        PolicyEvaluation evaluation = RunPolicyEvaluator.evaluate(policy, results, null);

        assertThat(evaluation.passed()).isFalse();
        assertThat(evaluation.violations()).anyMatch(v -> v.contains("max_errored_checks"));
    }

    @Property
    void passesWhenLatencyRegressionAtOrBelowThreshold(
            @ForAll @LongRange(min = 0, max = 500) long threshold,
            @ForAll @LongRange(min = 0, max = 500) long regressionMs) {
        if (regressionMs > threshold) {
            return;
        }
        RunPolicy policy = new RunPolicy(null, null, threshold);
        RunDiffDto diff = diffWithLatencyRegression(regressionMs);

        PolicyEvaluation evaluation = RunPolicyEvaluator.evaluate(policy, List.of(), diff);

        assertThat(evaluation.passed()).isTrue();
        assertThat(evaluation.maxLatencyRegressionMs()).isEqualTo(regressionMs);
    }

    @Property
    void failsWhenLatencyRegressionExceedsThreshold(@ForAll @LongRange(min = 0, max = 500) long threshold) {
        long regressionMs = threshold + 1;
        RunPolicy policy = new RunPolicy(null, null, threshold);
        RunDiffDto diff = diffWithLatencyRegression(regressionMs);

        PolicyEvaluation evaluation = RunPolicyEvaluator.evaluate(policy, List.of(), diff);

        assertThat(evaluation.passed()).isFalse();
        assertThat(evaluation.violations()).anyMatch(v -> v.contains("max_latency_regression_ms"));
    }

    private static RunDiffDto diffWithNewlyFailing(int count) {
        List<RunDiffEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(new RunDiffEntry(
                    "k" + i,
                    "K" + i,
                    RunDiffChangeType.NEWLY_FAILING,
                    CheckResultStatus.PASSED,
                    CheckResultStatus.FAILED,
                    200,
                    500,
                    10L,
                    20L));
        }
        return new RunDiffDto("r1", "r0", entries);
    }

    private static RunDiffDto diffWithLatencyRegression(long regressionMs) {
        RunDiffEntry entry = new RunDiffEntry(
                "k",
                "K",
                RunDiffChangeType.LATENCY_CHANGED,
                CheckResultStatus.PASSED,
                CheckResultStatus.PASSED,
                200,
                200,
                100L,
                100L + regressionMs);
        return new RunDiffDto("r1", "r0", List.of(entry));
    }

    private static List<CheckResultDto> erroredResults(int count) {
        List<CheckResultDto> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(new CheckResultDto(
                    "id" + i,
                    "run",
                    "check" + i,
                    "k" + i,
                    "K" + i,
                    CheckResultStatus.ERROR,
                    500,
                    10L,
                    "E",
                    "boom",
                    null,
                    List.of(),
                    Instant.now()));
        }
        return results;
    }
}
