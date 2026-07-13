package dev.backline.core.contract;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Per-check observed-contract capture settings from config or API sync.
 *
 * <p>When {@code enabled} is null, capture defaults to enabled (warn-by-default). When {@code severity}
 * is null, {@link ContractSeverity#WARN} is used. Ignore paths use a small syntax: {@code $.a.b} and
 * {@code []} array segments only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractSettingsDto(Boolean enabled, String severity, List<String> ignorePaths) {

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public ContractSeverity resolvedSeverity() {
        if (severity == null || severity.isBlank()) {
            return ContractSeverity.WARN;
        }
        return ContractSeverity.valueOf(severity.trim().toUpperCase());
    }

    public List<String> resolvedIgnorePaths() {
        return ignorePaths == null ? List.of() : List.copyOf(ignorePaths);
    }

    public static ContractSettingsDto defaults() {
        return new ContractSettingsDto(true, ContractSeverity.WARN.name().toLowerCase(), List.of());
    }
}
