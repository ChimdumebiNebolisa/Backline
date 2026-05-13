package dev.backline.api.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.persistence.entity.CheckResultEntity;
import dev.backline.core.api.dto.AssertionResultDto;
import dev.backline.core.api.dto.CheckResultDto;

import java.util.Collections;
import java.util.List;

public final class CheckResultMapper {

    private CheckResultMapper() {}

    public static CheckResultDto toDto(CheckResultEntity e, ObjectMapper mapper) {
        return new CheckResultDto(
                e.getId().toString(),
                e.getRunId().toString(),
                e.getCheckId() != null ? e.getCheckId().toString() : null,
                e.getCheckKey(),
                e.getCheckName(),
                e.getStatus(),
                e.getActualStatus(),
                e.getLatencyMs(),
                e.getErrorCode(),
                e.getErrorMessage(),
                e.getResponsePreview(),
                readAssertions(mapper, e.getAssertionsJson()),
                e.getCreatedAt());
    }

    public static List<AssertionResultDto> readAssertions(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("invalid assertions_json for check result", ex);
        }
    }
}
