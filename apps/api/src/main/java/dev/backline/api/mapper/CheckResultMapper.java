package dev.backline.api.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.persistence.entity.CheckResultEntity;
import dev.backline.core.api.dto.AssertionResultDto;
import dev.backline.core.api.dto.CheckResultDto;
import dev.backline.core.contract.ResponseContract;
import dev.backline.core.contract.ResponseContractStatus;
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
                e.getCreatedAt(),
                parseStatus(e.getResponseContractStatus()),
                e.getResponseContractHash(),
                readContract(mapper, e.getResponseContractJson()));
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

    /**
     * Deserializes observed contract JSON defensively. Corrupt contract JSON returns null without failing
     * the results endpoint.
     */
    public static Object readContract(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, ResponseContract.class);
        } catch (Exception ex) {
            log.warn("invalid response_contract_json for check result; omitting contract: {}", ex.getMessage());
            return null;
        }
    }

    private static ResponseContractStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ResponseContractStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("unknown response_contract_status {}; omitting", raw);
            return null;
        }
    }
}
