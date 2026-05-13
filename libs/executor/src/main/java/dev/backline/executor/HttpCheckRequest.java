package dev.backline.executor;

import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.check.HttpMethod;

import java.util.List;
import java.util.Map;

/**
 * Immutable definition of a single HTTP check to execute.
 *
 * <p>When {@code headers} is non-null, it is defensively copied so callers cannot mutate internal
 * state after construction.
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
        Map<String, String> headers) {

    public HttpCheckRequest {
        if (headers != null) {
            headers = Map.copyOf(headers);
        }
    }
}
