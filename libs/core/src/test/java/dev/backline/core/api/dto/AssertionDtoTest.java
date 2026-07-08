package dev.backline.core.api.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssertionDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesPathForDatabaseRoundTrip() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(new AssertionDto("$.id", 1, null)));

        assertThat(json).contains("\"path\":\"$.id\"");

        List<AssertionDto> parsed = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(parsed.getFirst().path()).isEqualTo("$.id");
        assertThat(parsed.getFirst().equalsValue()).isEqualTo(1);
    }
}
