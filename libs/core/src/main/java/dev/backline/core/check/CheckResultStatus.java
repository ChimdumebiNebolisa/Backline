package dev.backline.core.check;

/**
 * Outcome of executing a single HTTP check within a run.
 */
public enum CheckResultStatus {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED
}
