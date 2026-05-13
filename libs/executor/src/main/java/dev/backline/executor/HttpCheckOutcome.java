package dev.backline.executor;

import dev.backline.core.api.dto.AssertionResultDto;
import dev.backline.core.check.CheckResultStatus;

import java.util.List;

/**
 * Outcome of executing one {@link HttpCheckRequest}.
 *
 * <p>{@link #assertionResults()} is never null; it may be empty when no assertions were evaluated.
 */
public record HttpCheckOutcome(
        CheckResultStatus status,
        Integer actualStatus,
        Long latencyMs,
        String errorCode,
        String errorMessage,
        String responsePreview,
        List<AssertionResultDto> assertionResults) {

    public HttpCheckOutcome {
        assertionResults = assertionResults == null ? List.of() : List.copyOf(assertionResults);
    }
}
