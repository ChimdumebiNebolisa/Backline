package dev.backline.core.run;

/**
 * Lifecycle state of a submitted regression run in the worker queue.
 */
public enum RunStatus {
    QUEUED,
    RUNNING,
    PASSED,
    FAILED,
    ERROR,
    CANCELLED;

    public boolean isTerminal() {
        return this == PASSED || this == FAILED || this == ERROR || this == CANCELLED;
    }

    public boolean isClaimable() {
        return this == QUEUED;
    }
}
