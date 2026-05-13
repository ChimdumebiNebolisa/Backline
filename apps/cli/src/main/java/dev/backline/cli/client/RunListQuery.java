package dev.backline.cli.client;

/**
 * Query parameters for {@code GET /api/runs}; unset fields are omitted from the request.
 */
public record RunListQuery(String projectSlug, String environment, String status, Integer limit, Integer offset) {}
