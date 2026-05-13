package dev.backline.api.dto;

import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.check.HttpMethod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Validated check definition for sync; maps to {@link dev.backline.core.api.dto.CheckDefinitionDto}.
 */
public record CheckDefinitionBody(
        @NotBlank @Size(max = 120) String key,
        @NotBlank @Size(max = 200) String name,
        @NotNull HttpMethod method,
        @NotBlank
                @Pattern(
                        regexp = "^https?://[^\\s]+$",
                        message = "url must be an absolute http(s) URL")
                String url,
        @NotNull @Min(100) @Max(599) Integer expectedStatus,
        @Min(1) Integer maxLatencyMs,
        List<AssertionDto> assertions) {}
