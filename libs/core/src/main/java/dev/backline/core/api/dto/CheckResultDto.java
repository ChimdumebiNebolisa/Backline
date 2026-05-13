package dev.backline.core.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.backline.core.check.CheckResultStatus;

import java.time.Instant;
import java.util.List;

/**
 * API projection of a single check execution result within a run.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckResultDto(
        String id,
        String runId,
        String checkId,
        String checkKey,
        String checkName,
        CheckResultStatus status,
        Integer actualStatus,
        Long latencyMs,
        String errorCode,
        String errorMessage,
        String responsePreview,
        List<AssertionResultDto> assertions,
        Instant createdAt) {}
