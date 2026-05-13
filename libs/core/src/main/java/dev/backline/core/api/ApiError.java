package dev.backline.core.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Single structured error payload returned to API clients.
 */
public record ApiError(
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String field) {}
