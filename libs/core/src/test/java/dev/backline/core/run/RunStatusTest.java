package dev.backline.core.run;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunStatusTest {

    @Test
    void terminalStatuses() {
        assertThat(RunStatus.PASSED.isTerminal()).isTrue();
        assertThat(RunStatus.FAILED.isTerminal()).isTrue();
        assertThat(RunStatus.ERROR.isTerminal()).isTrue();
        assertThat(RunStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(RunStatus.QUEUED.isTerminal()).isFalse();
        assertThat(RunStatus.RUNNING.isTerminal()).isFalse();
    }

    @Test
    void claimableOnlyWhenQueued() {
        assertThat(RunStatus.QUEUED.isClaimable()).isTrue();
        assertThat(RunStatus.RUNNING.isClaimable()).isFalse();
        assertThat(RunStatus.PASSED.isClaimable()).isFalse();
    }
}
