package dev.backline.core.api.dto;

/**
 * Request body for creating a project.
 */
public record CreateProjectRequest(String slug, String name) {}
