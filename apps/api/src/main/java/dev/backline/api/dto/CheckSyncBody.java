package dev.backline.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CheckSyncBody(
        @NotBlank String projectSlug,
        @Size(max = 200) String projectName,
        @NotEmpty @Valid List<CheckDefinitionBody> checks) {}
