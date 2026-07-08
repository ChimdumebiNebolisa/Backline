package dev.backline.api.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.exception.ValidationFailedException;
import dev.backline.core.api.dto.AssertionDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssertionJsonMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void toJsonOrNull_nullOrEmpty_returnsNull() {
        assertThat(AssertionJsonMapper.toJsonOrNull(mapper, null)).isNull();
        assertThat(AssertionJsonMapper.toJsonOrNull(mapper, List.of())).isNull();
    }

    @Test
    void toJsonOrNull_sortsAssertionsDeterministically() {
        var b = new AssertionDto("$.b", null, null);
        var a = new AssertionDto("$.a", 1, null);
        String json = AssertionJsonMapper.toJsonOrNull(mapper, List.of(b, a));
        assertThat(json.indexOf("$.a")).isLessThan(json.indexOf("$.b"));
    }

    @Test
    void sortedCopy_nullOrEmpty_returnsEmptyList() {
        assertThat(AssertionJsonMapper.sortedCopy(null)).isEmpty();
        assertThat(AssertionJsonMapper.sortedCopy(List.of())).isEmpty();
    }

    @Test
    void toJsonOrNull_invalidSerializationThrowsValidationFailed() throws Exception {
        ObjectMapper broken = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
                throw new com.fasterxml.jackson.core.JsonProcessingException("boom") {};
            }
        };
        var assertion = new AssertionDto("$.x", 1, null);
        assertThatThrownBy(() -> AssertionJsonMapper.toJsonOrNull(broken, List.of(assertion)))
                .isInstanceOf(ValidationFailedException.class);
    }
}
