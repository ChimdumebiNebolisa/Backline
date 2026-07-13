package dev.backline.core.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.backline.core.check.HttpMethod;
import dev.backline.core.contract.ContractSettingsDto;

import java.time.Instant;
import java.util.List;

/**
 * API projection of a check definition keyed within a project.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckDto(
        String id,
        String projectId,
        String key,
        String name,
        HttpMethod method,
        String url,
        int expectedStatus,
        Integer maxLatencyMs,
        List<AssertionDto> assertions,
        ContractSettingsDto contract,
        String configHash,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public CheckDto(
            String id,
            String projectId,
            String key,
            String name,
            HttpMethod method,
            String url,
            int expectedStatus,
            Integer maxLatencyMs,
            List<AssertionDto> assertions,
            String configHash,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                projectId,
                key,
                name,
                method,
                url,
                expectedStatus,
                maxLatencyMs,
                assertions,
                null,
                configHash,
                active,
                createdAt,
                updatedAt);
    }
}
