package dev.backline.api.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.persistence.entity.CheckResultEntity;
import dev.backline.core.check.CheckResultStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckResultMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void toDto_mapsAllScalarFields() {
        UUID id = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID checkId = UUID.randomUUID();
        Instant created = Instant.parse("2024-01-01T00:00:00Z");
        CheckResultEntity entity = new CheckResultEntity();
        entity.setId(id);
        entity.setRunId(runId);
        entity.setCheckId(checkId);
        entity.setCheckKey("health");
        entity.setCheckName("Health");
        entity.setStatus(CheckResultStatus.PASSED);
        entity.setActualStatus(200);
        entity.setLatencyMs(42L);
        entity.setErrorCode("ERR");
        entity.setErrorMessage("msg");
        entity.setResponsePreview("preview");
        entity.setAssertionsJson("[{\"path\":\"$.id\",\"passed\":true}]");
        entity.setCreatedAt(created);

        var dto = CheckResultMapper.toDto(entity, mapper);
        assertThat(dto.id()).isEqualTo(id.toString());
        assertThat(dto.runId()).isEqualTo(runId.toString());
        assertThat(dto.checkId()).isEqualTo(checkId.toString());
        assertThat(dto.checkKey()).isEqualTo("health");
        assertThat(dto.status()).isEqualTo(CheckResultStatus.PASSED);
        assertThat(dto.latencyMs()).isEqualTo(42L);
        assertThat(dto.assertions()).hasSize(1);
    }

    @Test
    void readAssertions_nullOrBlank_returnsEmptyList() {
        assertThat(CheckResultMapper.readAssertions(mapper, null)).isEmpty();
        assertThat(CheckResultMapper.readAssertions(mapper, "  ")).isEmpty();
    }

    @Test
    void readAssertions_invalidJson_throwsIllegalStateException() {
        assertThatThrownBy(() -> CheckResultMapper.readAssertions(mapper, "not-json"))
                .isInstanceOf(IllegalStateException.class);
    }
}
