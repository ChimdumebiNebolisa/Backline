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

        String slug2 = "proj-" + UUID.randomUUID().toString().substring(0, 8);
        String slug3 = "proj-" + UUID.randomUUID().toString().substring(0, 8);
        restTemplate.postForEntity(
                "/api/projects",
                new HttpEntity<>(objectMapper.writeValueAsString(Map.of("slug", slug2, "name", "My Project 2")), headers),
                String.class);
        restTemplate.postForEntity(
                "/api/projects",
                new HttpEntity<>(objectMapper.writeValueAsString(Map.of("slug", slug3, "name", "My Project 3")), headers),
                String.class);

        ResponseEntity<String> firstPage = restTemplate.getForEntity("/api/projects?limit=2&offset=0", String.class);
        ResponseEntity<String> secondPage = restTemplate.getForEntity("/api/projects?limit=2&offset=1", String.class);
        var firstData = objectMapper.readTree(firstPage.getBody()).path("data");
        var secondData = objectMapper.readTree(secondPage.getBody()).path("data");
        assertThat(firstData.get(0).path("id").asText()).isNotEqualTo(secondData.get(0).path("id").asText());
    }
}
