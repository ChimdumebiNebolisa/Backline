package dev.backline.core.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.run.RunStatus;

import java.time.Instant;

/**
 * One check's result history point for charting or tabular history views.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckHistoryEntry(
        String runId,
        RunStatus runStatus,
        CheckResultStatus resultStatus,
        Integer actualStatus,
        Long latencyMs,
        Instant createdAt) {}
