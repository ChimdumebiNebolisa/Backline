package dev.backline.core.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.backline.core.run.RunStatus;

import java.time.Instant;

/**
 * API projection of a run row including timing and idempotency metadata.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunDto(
        String id,
        String projectId,
        String environment,
        RunStatus status,
        String configHash,
        String source,
        String idempotencyKey,
        Instant queuedAt,
        Instant startedAt,
        Instant finishedAt,
        int attemptCount) {}
