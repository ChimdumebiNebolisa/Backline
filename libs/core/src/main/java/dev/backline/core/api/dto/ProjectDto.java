package dev.backline.core.api.dto;

import java.time.Instant;

/**
 * API projection of a persisted project row.
 */
public record ProjectDto(
        String id, String slug, String name, Instant createdAt, Instant updatedAt) {}
