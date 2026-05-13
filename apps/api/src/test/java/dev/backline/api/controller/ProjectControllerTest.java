package dev.backline.api.controller;

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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectControllerTest extends PostgresTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createListFetchAndDuplicateSlug() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String slug = "proj-" + UUID.randomUUID().toString().substring(0, 8);
        String body = objectMapper.writeValueAsString(Map.of("slug", slug, "name", "My Project"));
        ResponseEntity<String> created =
                restTemplate.postForEntity("/api/projects", new HttpEntity<>(body, headers), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = objectMapper.readTree(created.getBody()).path("data").path("id").asText();

        ResponseEntity<String> list = restTemplate.getForEntity("/api/projects?limit=10&offset=0", String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(list.getBody()).path("data").isArray()).isTrue();

        ResponseEntity<String> one = restTemplate.getForEntity("/api/projects/" + id, String.class);
        assertThat(one.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> dup =
                restTemplate.postForEntity("/api/projects", new HttpEntity<>(body, headers), String.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
