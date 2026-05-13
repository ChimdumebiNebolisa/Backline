package dev.backline.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRunBody(
        @NotBlank @Size(max = 120) String projectSlug,
        @NotBlank @Size(max = 60) String environment,
        @NotBlank @Size(max = 128) String configHash,
        @Size(max = 180) String idempotencyKey,
        @Size(max = 60) String source) {}
