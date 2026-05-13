package dev.backline.core.api.dto;

/**
 * Classifies how a check differs between two compared runs.
 */
public enum RunDiffChangeType {
    NEWLY_FAILING,
    NEWLY_PASSING,
    STILL_FAILING,
    STILL_PASSING,
    NEWLY_ADDED,
    REMOVED,
    STATUS_CODE_CHANGED,
    LATENCY_CHANGED,
    ASSERTION_CHANGED
}
