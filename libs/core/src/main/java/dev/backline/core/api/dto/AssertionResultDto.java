package dev.backline.core.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Outcome of evaluating one assertion against the HTTP response body.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssertionResultDto(
        String path,
        Object expectedEquals,
        Boolean expectedExists,
        Object actual,
        boolean passed,
        String message) {}
