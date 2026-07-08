package dev.backline.core.api.dto;

/**
 * Strategy for selecting the baseline run used by run diff comparisons.
 */
public enum DiffBaselineStrategy {
    PREVIOUS_COMPLETED,
    LAST_PASSED,
    FIXED_RUN
}
