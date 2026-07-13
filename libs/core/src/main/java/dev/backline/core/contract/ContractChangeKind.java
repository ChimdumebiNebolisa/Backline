package dev.backline.core.contract;

/**
 * How a single structural path changed between two observed contracts.
 */
public enum ContractChangeKind {
    ADDED,
    REMOVED,
    TYPE_CHANGED
}
