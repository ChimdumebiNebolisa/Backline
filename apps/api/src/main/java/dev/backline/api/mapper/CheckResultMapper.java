package dev.backline.api.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.persistence.entity.CheckResultEntity;
import dev.backline.core.api.dto.AssertionResultDto;
import dev.backline.core.api.dto.CheckResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public final class CheckResultMapper {

    private static final Logger log = LoggerFactory.getLogger(CheckResultMapper.class);

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

    /**
     * Deserializes the stored assertion results, tolerating corrupt or malformed {@code assertions_json}.
     *
     * <p>A single unparsable row must not turn a read endpoint (for example
     * {@code GET /api/runs/{runId}/results}) into a 500. On failure this logs a warning and returns an
     * empty list so the rest of the check result is still served.
     */
    public static List<AssertionResultDto> readAssertions(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("invalid assertions_json for check result; returning empty assertions: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }
}
