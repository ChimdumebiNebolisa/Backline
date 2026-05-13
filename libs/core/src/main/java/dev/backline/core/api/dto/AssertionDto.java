package dev.backline.core.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Assertion rule attached to a check; JSON property {@code equals} maps to {@link #equalsValue()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssertionDto(
        String path,
        @JsonProperty("equals") Object equalsValue,
        Boolean exists) {}
