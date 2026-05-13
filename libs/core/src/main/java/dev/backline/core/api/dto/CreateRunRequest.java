package dev.backline.core.api.dto;

/**
 * Request body for enqueueing a new regression run.
 */
public record CreateRunRequest(
        String projectSlug,
        String environment,
        String configHash,
        String idempotencyKey,
        String source) {}
