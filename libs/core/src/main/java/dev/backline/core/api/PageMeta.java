package dev.backline.core.api;

/**
 * Pagination metadata for list API responses.
 */
public record PageMeta(int limit, int offset, long total) {}
