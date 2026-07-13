package dev.backline.api.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.persistence.entity.CheckEntity;
import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.api.dto.CheckDto;
import dev.backline.core.contract.ContractSettingsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public final class CheckMapper {

    private static final Logger log = LoggerFactory.getLogger(CheckMapper.class);

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
                readContract(mapper, e.getContractJson()),
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

    public static ContractSettingsDto readContract(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, ContractSettingsDto.class);
        } catch (Exception ex) {
            log.warn("invalid contract_json for check; ignoring contract settings: {}", ex.getMessage());
            return null;
        }
    }
}
