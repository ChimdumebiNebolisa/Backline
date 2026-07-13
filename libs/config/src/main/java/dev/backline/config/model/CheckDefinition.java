package dev.backline.config.model;

import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.check.HttpMethod;

import java.util.List;

/**
 * One HTTP check from config YAML; fields match naming used by {@code PropertyNamingStrategies.SNAKE_CASE}
 * on the YAML {@link com.fasterxml.jackson.databind.ObjectMapper}.
 */
public record CheckDefinition(
        String key,
        String name,
        HttpMethod method,
        String url,
        int expectedStatus,
        Integer maxLatencyMs,
        List<AssertionDto> assertions,
        ContractSettings contract) {}
