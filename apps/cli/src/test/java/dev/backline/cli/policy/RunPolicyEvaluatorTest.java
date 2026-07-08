package dev.backline.cli.policy;

import dev.backline.config.model.RunPolicy;
import dev.backline.core.api.dto.CheckResultDto;
import dev.backline.core.api.dto.RunDiffChangeType;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.api.dto.RunDiffEntry;
import dev.backline.core.check.CheckResultStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunPolicyEvaluatorTest {

    @Test
    void failsWhenNewlyFailingExceedsPolicy() {
        RunPolicy policy = new RunPolicy(0, 0, 100L);
        RunDiffDto diff = new RunDiffDto("r1", "r0", List.of(
                new RunDiffEntry("k", "K", RunDiffChangeType.NEWLY_FAILING, CheckResultStatus.PASSED,
                        CheckResultStatus.FAILED, 200, 500, 10L, 20L)));

        PolicyEvaluation evaluation = RunPolicyEvaluator.evaluate(policy, List.of(), diff);

        assertThat(evaluation.passed()).isFalse();
        assertThat(evaluation.newlyFailingCount()).isEqualTo(1);
        assertThat(evaluation.violations()).anyMatch(v -> v.contains("max_newly_failing"));
    }

    @Test
    void passesWhenWithinThresholds() {
        RunPolicy policy = new RunPolicy(2, 1, 300L);
        RunDiffDto diff = new RunDiffDto("r1", "r0", List.of(
                new RunDiffEntry("k", "K", RunDiffChangeType.LATENCY_CHANGED, CheckResultStatus.PASSED,
                        CheckResultStatus.PASSED, 200, 200, 100L, 250L)));
        List<CheckResultDto> results = List.of(new CheckResultDto(
                "id", "run", "check", "k", "K", CheckResultStatus.ERROR, 500, 10L,
                "E", "boom", null, List.of(), Instant.now()));

        PolicyEvaluation evaluation = RunPolicyEvaluator.evaluate(policy, results, diff);

        assertThat(evaluation.passed()).isTrue();
        assertThat(evaluation.erroredChecksCount()).isEqualTo(1);
        assertThat(evaluation.maxLatencyRegressionMs()).isEqualTo(150L);
    }
}
