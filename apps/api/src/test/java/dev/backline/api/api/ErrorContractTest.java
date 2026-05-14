package dev.backline.api.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorContractTest extends PostgresTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void postProjectBlankSlugReturnsValidationError() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{\"slug\":\"\",\"name\":\"n\"}", headers);
        ResponseEntity<String> res =
                restTemplate.postForEntity("/api/projects", request, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode root = objectMapper.readTree(res.getBody());
        assertThat(root.path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(root.path("error").path("field").asText()).isEqualTo("slug");
    }

    @Test
    void getUnknownProjectReturnsNotFound() throws Exception {
        ResponseEntity<String> res = restTemplate.getForEntity(
                "/api/projects/00000000-0000-0000-0000-000000000000", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode root = objectMapper.readTree(res.getBody());
        assertThat(root.path("error").path("code").asText()).isEqualTo("NOT_FOUND");
    }
}
