package dev.backline.config.model;

import java.util.List;

/**
 * Per-check observed JSON response-contract capture settings from {@code backline.yml}.
 *
 * <p>YAML keys use snake_case ({@code ignore_paths}). When {@code enabled} is omitted, capture defaults
 * to on (warn-by-default). Severity defaults to {@code warn}.
 */
public record ContractSettings(
        Boolean enabled,
        String severity,
        List<String> ignorePaths) {}
