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

    @Test
    void serializesAllOperatorsWithSnakeCaseEqualsAlias() throws Exception {
        AssertionDto assertion = new AssertionDto(
                "$.n",
                null,
                null,
                "old",
                "part",
                "^[0-9]+$",
                1.0,
                2.0,
                3.0,
                4.0);

        String json = objectMapper.writeValueAsString(assertion);
        assertThat(json).contains("\"not_equals\":\"old\"");
        assertThat(json).doesNotContain("\"equals\"");

        AssertionDto parsed = objectMapper.readValue(json, AssertionDto.class);
        assertThat(parsed).isEqualTo(assertion);
        assertThat(parsed.hashCode()).isEqualTo(assertion.hashCode());
        assertThat(parsed).isNotEqualTo(new AssertionDto("$.other", 1, null));
        assertThat(parsed).isEqualTo(parsed);
        assertThat(parsed).isNotEqualTo("not-an-assertion");
    }
}
