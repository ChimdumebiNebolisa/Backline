package dev.backline.api.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest extends PostgresTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthReturnsUp() throws Exception {
        ResponseEntity<String> res = restTemplate.getForEntity("/api/health", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = objectMapper.readTree(res.getBody());
        assertThat(root.path("data").path("status").asText()).isEqualTo("UP");
    }
}
