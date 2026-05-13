package dev.backline.core.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.backline.core.run.RunEventType;

import java.time.Instant;

/**
 * API projection of an auditable run event row.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunEventDto(String id, String runId, RunEventType type, String message, Instant createdAt) {}
