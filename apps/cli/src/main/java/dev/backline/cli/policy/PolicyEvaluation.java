package dev.backline.cli.policy;

import java.util.List;

/**
 * Result of applying run policy thresholds to a completed run.
 */
public record PolicyEvaluation(
        boolean passed,
        int newlyFailingCount,
        int erroredChecksCount,
        long maxLatencyRegressionMs,
        List<String> violations) {}
