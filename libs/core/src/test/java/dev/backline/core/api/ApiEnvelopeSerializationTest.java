package dev.backline.core.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.core.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiEnvelopeSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dataResponseWrapsPayload() throws Exception {
        DataResponse<Map<String, String>> response = DataResponse.of(Map.of("id", "run-1"));

        String json = objectMapper.writeValueAsString(response);
        DataResponse<Map<String, String>> parsed =
                objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(parsed.data()).containsEntry("id", "run-1");
    }

    @Test
    void listResponseIncludesPageMeta() throws Exception {
        ListResponse<String> response = ListResponse.of(List.of("a", "b"), new PageMeta(10, 0, 2));

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"limit\":10").contains("\"offset\":0").contains("\"total\":2");

        ListResponse<String> parsed = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(parsed.data()).containsExactly("a", "b");
        assertThat(parsed.page().limit()).isEqualTo(10);
        assertThat(parsed.page().offset()).isEqualTo(0);
        assertThat(parsed.page().total()).isEqualTo(2);
    }

    @Test
    void errorResponseOmitsNullField() throws Exception {
        ErrorResponse response = ErrorResponse.of(ErrorCode.VALIDATION_ERROR, "bad input");

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"code\":\"VALIDATION_ERROR\"");
        assertThat(json).contains("\"message\":\"bad input\"");
        assertThat(json).doesNotContain("\"field\"");

        ErrorResponse parsed = objectMapper.readValue(json, ErrorResponse.class);
        assertThat(parsed.error().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(parsed.error().field()).isNull();
    }

    @Test
    void errorResponseIncludesFieldWhenPresent() throws Exception {
        ErrorResponse response = ErrorResponse.of(ErrorCode.VALIDATION_ERROR, "required", "slug");

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"field\":\"slug\"");

        ErrorResponse parsed = objectMapper.readValue(json, ErrorResponse.class);
        assertThat(parsed.error().field()).isEqualTo("slug");
    }
}
