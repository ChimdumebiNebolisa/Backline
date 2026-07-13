package dev.backline.worker.loop;

import dev.backline.core.run.RunStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerTerminalStatusTest {

    @Test
    void allPassingResolvesToPassed() {
        assertThat(WorkerLoop.resolveTerminalStatus(false, false)).isEqualTo(RunStatus.PASSED);
    }

    @Test
    void anyErrorWithoutFailureResolvesToError() {
        assertThat(WorkerLoop.resolveTerminalStatus(true, false)).isEqualTo(RunStatus.ERROR);
    }

    @Test
    void anyFailureResolvesToFailed() {
        assertThat(WorkerLoop.resolveTerminalStatus(false, true)).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void assertionFailureTakesPrecedenceOverTransportError() {
        assertThat(WorkerLoop.resolveTerminalStatus(true, true)).isEqualTo(RunStatus.FAILED);
    }
}
