package dev.backline.api.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.core.api.dto.AssertionResultDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckResultMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readAssertions_nullOrBlank_returnsEmpty() {
        assertThat(CheckResultMapper.readAssertions(mapper, null)).isEmpty();
        assertThat(CheckResultMapper.readAssertions(mapper, "   ")).isEmpty();
    }

    @Test
    void readAssertions_validJson_parses() {
        String json = "[{\"path\":\"$.id\",\"passed\":true}]";
        List<AssertionResultDto> parsed = CheckResultMapper.readAssertions(mapper, json);
        assertThat(parsed).singleElement()
                .satisfies(a -> {
                    assertThat(a.path()).isEqualTo("$.id");
                    assertThat(a.passed()).isTrue();
                });
    }

    @Test
    void readAssertions_corruptJson_returnsEmptyInsteadOfThrowing() {
        assertThat(CheckResultMapper.readAssertions(mapper, "{ not valid json")).isEmpty();
        assertThat(CheckResultMapper.readAssertions(mapper, "\"a string, not an array\"")).isEmpty();
    }
}
