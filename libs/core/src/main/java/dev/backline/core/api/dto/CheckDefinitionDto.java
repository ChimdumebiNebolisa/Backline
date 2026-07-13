package dev.backline.core.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.backline.core.check.HttpMethod;
import dev.backline.core.contract.ContractSettingsDto;

import java.util.List;

/**
 * Portable check definition used during sync without persistence identifiers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckDefinitionDto(
        String key,
        String name,
        HttpMethod method,
        String url,
        int expectedStatus,
        Integer maxLatencyMs,
        List<AssertionDto> assertions,
        ContractSettingsDto contract) {

    public CheckDefinitionDto(
            String key,
            String name,
            HttpMethod method,
            String url,
            int expectedStatus,
            Integer maxLatencyMs,
            List<AssertionDto> assertions) {
        this(key, name, method, url, expectedStatus, maxLatencyMs, assertions, null);
    }
}
