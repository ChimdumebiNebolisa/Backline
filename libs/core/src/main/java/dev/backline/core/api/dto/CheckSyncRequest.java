package dev.backline.core.api.dto;

import java.util.List;

/**
 * Request body for upserting checks for a project from config sync.
 */
public record CheckSyncRequest(String projectSlug, String projectName, List<CheckDefinitionDto> checks) {}
