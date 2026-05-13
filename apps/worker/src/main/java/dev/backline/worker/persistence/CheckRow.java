package dev.backline.worker.persistence;

import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.check.HttpMethod;

import java.util.List;
import java.util.UUID;

public record CheckRow(
        UUID checkId,
        String key,
        String name,
        HttpMethod method,
        String url,
        int expectedStatus,
        Integer maxLatencyMs,
        List<AssertionDto> assertions,
        String configHash) {}
