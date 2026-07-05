package dev.backline.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.persistence.entity.CheckEntity;
import dev.backline.api.persistence.repository.CheckRepository;
import dev.backline.api.persistence.repository.ProjectRepository;
import dev.backline.api.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckControllerTest extends PostgresTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CheckRepository checkRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void syncCreatesProjectThenDeactivateRemovedCheck() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String slug = "chk-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> sync1 = Map.of(
                "projectSlug",
                slug,
                "projectName",
                "Chk",
                "checks",
                List.of(
                        Map.of(
                                "key",
                                "a",
                                "name",
                                "A",
                                "method",
                                "GET",
                                "url",
                                "http://localhost:8081/health",
                                "expectedStatus",
                                200),
                        Map.of(
                                "key",
                                "b",
                                "name",
                                "B",
                                "method",
                                "GET",
                                "url",
                                "http://localhost:8081/health",
                                "expectedStatus",
                                200)));
        ResponseEntity<String> r1 = restTemplate.postForEntity(
                "/api/checks/sync",
                new HttpEntity<>(objectMapper.writeValueAsString(sync1), headers),
                String.class);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data1 = objectMapper.readTree(r1.getBody()).path("data");
        assertThat(data1.isArray()).isTrue();
        assertThat(data1.size()).isEqualTo(2);

        Map<String, Object> sync2 = Map.of(
                "projectSlug",
                slug,
                "checks",
                List.of(Map.of(
                        "key",
                        "a",
                        "name",
                        "A",
                        "method",
                        "GET",
                        "url",
                        "http://localhost:8081/health",
                        "expectedStatus",
                        200)));
        ResponseEntity<String> r2 = restTemplate.postForEntity(
                "/api/checks/sync",
                new HttpEntity<>(objectMapper.writeValueAsString(sync2), headers),
                String.class);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data2 = objectMapper.readTree(r2.getBody()).path("data");
        assertThat(data2.size()).isEqualTo(1);

        String checkAId = objectMapper.readTree(r1.getBody()).path("data").get(0).path("id").asText();
        ResponseEntity<String> hist =
                restTemplate.getForEntity("/api/checks/" + checkAId + "/history?limit=5&offset=0", String.class);
        assertThat(hist.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(hist.getBody()).path("page").path("limit").asInt()).isEqualTo(5);

        var projectId = projectRepository.findBySlug(slug).orElseThrow().getId();
        List<CheckEntity> all = checkRepository.findByProjectId(projectId);
        assertThat(all).hasSize(2);
        CheckEntity b =
                all.stream().filter(c -> "b".equals(c.getKey())).findFirst().orElseThrow();
        assertThat(b.isActive()).isFalse();

        String badStatus = objectMapper.writeValueAsString(Map.of(
                "projectSlug",
                slug,
                "checks",
                List.of(Map.of(
                        "key",
                        "x",
                        "name",
                        "X",
                        "method",
                        "GET",
                        "url",
                        "http://localhost:8081/health",
                        "expectedStatus",
                        700))));
        ResponseEntity<String> bad = restTemplate.postForEntity(
                "/api/checks/sync", new HttpEntity<>(badStatus, headers), String.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String badUrl = objectMapper.writeValueAsString(Map.of(
                "projectSlug",
                slug,
                "checks",
                List.of(Map.of(
                        "key",
                        "z",
                        "name",
                        "Z",
                        "method",
                        "GET",
                        "url",
                        "not-a-url",
                        "expectedStatus",
                        200))));
        ResponseEntity<String> badUrlRes = restTemplate.postForEntity(
                "/api/checks/sync", new HttpEntity<>(badUrl, headers), String.class);
        assertThat(badUrlRes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String hostlessUrl = objectMapper.writeValueAsString(Map.of(
                "projectSlug",
                slug,
                "checks",
                List.of(Map.of(
                        "key",
                        "h",
                        "name",
                        "H",
                        "method",
                        "GET",
                        "url",
                        "http:///health",
                        "expectedStatus",
                        200))));
        ResponseEntity<String> hostlessUrlRes = restTemplate.postForEntity(
                "/api/checks/sync", new HttpEntity<>(hostlessUrl, headers), String.class);
        assertThat(hostlessUrlRes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
