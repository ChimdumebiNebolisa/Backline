package dev.backline.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Per-check observed response-contract settings on sync.
 */
public record ContractSettingsBody(
        Boolean enabled,
        @Pattern(regexp = "(?i)warn|block", message = "severity must be warn or block") String severity,
        @Size(max = 32) List<@Size(max = 200) String> ignorePaths) {}
