package dev.backline.api.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.persistence.entity.CheckEntity;
import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.api.dto.CheckDto;

import java.util.Collections;
import java.util.List;

public final class CheckMapper {

    private CheckMapper() {}

    public static CheckDto toDto(CheckEntity e, ObjectMapper mapper) {
        return new CheckDto(
                e.getId().toString(),
                e.getProjectId().toString(),
                e.getKey(),
                e.getName(),
                e.getMethod(),
                e.getUrl(),
                e.getExpectedStatus(),
                e.getMaxLatencyMs(),
                readAssertions(mapper, e.getAssertionsJson()),
                e.getConfigHash(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    public static List<AssertionDto> readAssertions(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("invalid assertions_json for check", ex);
        }
    }
}
