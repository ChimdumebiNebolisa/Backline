package dev.backline.executor;

import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.api.dto.AssertionResultDto;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.contract.ResponseContractStatus;

import java.util.List;

/**
 * Outcome of executing one {@link HttpCheckRequest}.
 *
 * <p>{@link #assertionResults()} is never null; it may be empty when no assertions were evaluated.
 * Contract fields describe observed JSON shape capture and do not alter {@link #status()}.
 */
public record HttpCheckOutcome(
        CheckResultStatus status,
        Integer actualStatus,
        Long latencyMs,
        String errorCode,
        String errorMessage,
        String responsePreview,
        List<AssertionResultDto> assertionResults,
        ResponseContractStatus responseContractStatus,
        String responseContractJson,
        String responseContractHash) {

    public HttpCheckOutcome {
        assertionResults = assertionResults == null ? List.of() : List.copyOf(assertionResults);
    }

    /** Convenience for transport / invalid-URL errors with no contract capture. */
    public static HttpCheckOutcome withoutContract(
            CheckResultStatus status,
            Integer actualStatus,
            Long latencyMs,
            String errorCode,
            String errorMessage,
            String responsePreview,
            List<AssertionResultDto> assertionResults) {
        return new HttpCheckOutcome(
                status,
                actualStatus,
                latencyMs,
                errorCode,
                errorMessage,
                responsePreview,
                assertionResults,
                null,
                null,
                null);
    }
}
