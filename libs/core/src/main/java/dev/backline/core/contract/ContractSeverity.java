package dev.backline.core.contract;

/**
 * Reporting severity for observed contract drift. Does not change check HTTP result status by itself.
 */
public enum ContractSeverity {
    WARN,
    BLOCK
}
