package dev.backline.config.model;

/**
 * Optional run policy limits used by CLI enforcement after a run reaches a terminal state.
 */
public record RunPolicy(
        Integer maxNewlyFailing,
        Integer maxErroredChecks,
        Long maxLatencyRegressionMs) {}
