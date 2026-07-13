package dev.backline.executor;

import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.check.HttpMethod;
import dev.backline.core.contract.ContractSettingsDto;

import java.util.List;
import java.util.Map;

/**
 * Immutable definition of a single HTTP check to execute.
 *
 * <p>When {@code headers} is non-null, it is defensively copied so callers cannot mutate internal
 * state after construction. {@code contract} is warn-by-default capture settings; null means enabled
 * with empty ignore paths.
 */
public record HttpCheckRequest(
        String checkId,
        String checkKey,
        String checkName,
        HttpMethod method,
        String url,
        int expectedStatus,
        Integer maxLatencyMs,
        List<AssertionDto> assertions,
        Map<String, String> headers,
        ContractSettingsDto contract) {

    public HttpCheckRequest {
        if (headers != null) {
            headers = Map.copyOf(headers);
        }
    }

    /** Backward-compatible constructor without contract settings. */
    public HttpCheckRequest(
            String checkId,
            String checkKey,
            String checkName,
            HttpMethod method,
            String url,
            int expectedStatus,
            Integer maxLatencyMs,
            List<AssertionDto> assertions,
            Map<String, String> headers) {
        this(checkId, checkKey, checkName, method, url, expectedStatus, maxLatencyMs, assertions, headers, null);
    }
}
