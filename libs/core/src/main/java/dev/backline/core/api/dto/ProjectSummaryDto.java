package dev.backline.core.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Aggregated project metrics with embedded project and last run snapshots.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectSummaryDto(
        ProjectDto project,
        long totalRuns,
        long passedRuns,
        long failedRuns,
        long erroredRuns,
        RunDto lastRun) {}
