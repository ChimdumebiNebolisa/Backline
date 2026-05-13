package dev.backline.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Validated HTTP body for {@code POST /api/projects}; mirrors {@link dev.backline.core.api.dto.CreateProjectRequest}
 * with Jakarta constraints.
 */
public record CreateProjectBody(
        @NotBlank(message = "slug is required")
                @Pattern(
                        regexp = "^[a-z0-9-]{1,120}$",
                        message = "slug must be 1-120 lowercase letters, digits, or dashes")
                String slug,
        @NotBlank(message = "name is required") @Size(max = 200) String name) {}
