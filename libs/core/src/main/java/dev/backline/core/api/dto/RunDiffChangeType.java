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
    ASSERTION_CHANGED,
    /** Primary change is a breaking observed response-contract drift. */
    CONTRACT_BREAKING,
    /** Primary change is an additive observed response-contract drift. */
    CONTRACT_ADDITIVE,
    /** Primary change is noisy observed response-contract drift (e.g. new nullability). */
    CONTRACT_NOISY,
    /** Primary change is that contract capture was unavailable on one or both sides. */
    CONTRACT_UNAVAILABLE
}
