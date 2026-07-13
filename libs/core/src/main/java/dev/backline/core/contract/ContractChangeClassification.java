package dev.backline.core.contract;

/**
 * Overall classification of observed contract drift between two check results.
 */
public enum ContractChangeClassification {
    BREAKING,
    ADDITIVE,
    NOISY,
    UNCHANGED,
    UNAVAILABLE
}
