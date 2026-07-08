package dev.backline.cli.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunPolicyProfileTest {

    @Test
    void fromCliValueAcceptsHyphenatedWarnOnly() {
        assertThat(RunPolicyProfile.fromCliValue("warn-only")).isEqualTo(RunPolicyProfile.WARN_ONLY);
        assertThat(RunPolicyProfile.fromCliValue("strict")).isEqualTo(RunPolicyProfile.STRICT);
    }

    @Test
    void strictPresetUsesZeroThresholds() {
        assertThat(RunPolicyProfile.STRICT.toPolicy().maxNewlyFailing()).isZero();
        assertThat(RunPolicyProfile.STRICT.toPolicy().maxErroredChecks()).isZero();
        assertThat(RunPolicyProfile.STRICT.toPolicy().maxLatencyRegressionMs()).isNull();
    }

    @Test
    void warnOnlyPresetDisablesThresholds() {
        assertThat(RunPolicyProfile.WARN_ONLY.toPolicy().maxNewlyFailing()).isNull();
        assertThat(RunPolicyProfile.WARN_ONLY.toPolicy().maxErroredChecks()).isNull();
        assertThat(RunPolicyProfile.WARN_ONLY.toPolicy().maxLatencyRegressionMs()).isNull();
    }
}
