package dev.backline.core.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.backline.core.check.CheckResultStatus;

/**
 * One row in a run-vs-run diff describing how a check's outcome changed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunDiffEntry(
        String checkKey,
        String checkName,
        RunDiffChangeType changeType,
        CheckResultStatus previousStatus,
        CheckResultStatus currentStatus,
        Integer previousActualStatus,
        Integer currentActualStatus,
        Long previousLatencyMs,
        Long currentLatencyMs) {}
