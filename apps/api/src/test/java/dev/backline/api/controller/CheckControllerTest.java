package dev.backline.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.persistence.entity.CheckEntity;
import dev.backline.api.persistence.entity.CheckResultEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.repository.CheckRepository;
import dev.backline.api.persistence.repository.CheckResultRepository;
import dev.backline.api.persistence.repository.ProjectRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.api.support.PostgresTestBase;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.run.RunStatus;
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

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private CheckResultRepository checkResultRepository;

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
        seedHistoryRows(UUID.fromString(checkAId), slug);
        ResponseEntity<String> hist =
                restTemplate.getForEntity("/api/checks/" + checkAId + "/history?limit=5&offset=0", String.class);
        assertThat(hist.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(hist.getBody()).path("page").path("limit").asInt()).isEqualTo(5);
        ResponseEntity<String> histOffset =
                restTemplate.getForEntity("/api/checks/" + checkAId + "/history?limit=1&offset=1", String.class);
        var histFirstData = objectMapper.readTree(hist.getBody()).path("data");
        var histOffsetData = objectMapper.readTree(histOffset.getBody()).path("data");
        if (histFirstData.size() > 1 && histOffsetData.size() > 0) {
            assertThat(histFirstData.get(0).path("runId").asText()).isNotEqualTo(histOffsetData.get(0).path("runId").asText());
        }

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

    @Test
    void syncRejectsAssertionWithoutOperation() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String slug = "chk-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> request = Map.of(
                "projectSlug",
                slug,
                "checks",
                List.of(Map.of(
                        "key",
                        "assertion",
                        "name",
                        "Assertion",
                        "method",
                        "GET",
                        "url",
                        "http://localhost:8081/health",
                        "expectedStatus",
                        200,
                        "assertions",
                        List.of(Map.of("path", "$.id")))));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/checks/sync",
                new HttpEntity<>(objectMapper.writeValueAsString(request), headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var error = objectMapper.readTree(response.getBody()).path("error");
        assertThat(error.path("message").asText()).contains("assertion must set exactly one supported operator");
        assertThat(error.path("field").asText()).isEqualTo("checks.assertions");
    }

    @Test
    void syncRejectsAssertionWithEqualsAndExists() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String slug = "chk-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> request = Map.of(
                "projectSlug",
                slug,
                "checks",
                List.of(Map.of(
                        "key",
                        "assertion",
                        "name",
                        "Assertion",
                        "method",
                        "GET",
                        "url",
                        "http://localhost:8081/health",
                        "expectedStatus",
                        200,
                        "assertions",
                        List.of(Map.of("path", "$.id", "equals", 1, "exists", true)))));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/checks/sync",
                new HttpEntity<>(objectMapper.writeValueAsString(request), headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var error = objectMapper.readTree(response.getBody()).path("error");
        assertThat(error.path("message").asText()).contains("assertion must set only one operator");
        assertThat(error.path("field").asText()).isEqualTo("checks.assertions");
    }

    @Test
    void syncAcceptsExtendedAssertionOperators() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String slug = "chk-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> request = Map.of(
                "projectSlug",
                slug,
                "checks",
                List.of(Map.of(
                        "key",
                        "assertion",
                        "name",
                        "Assertion",
                        "method",
                        "GET",
                        "url",
                        "http://localhost:8081/health",
                        "expectedStatus",
                        200,
                        "assertions",
                        List.of(
                                Map.of("path", "$.name", "contains", "alice"),
                                Map.of("path", "$.id", "gt", 0),
                                Map.of("path", "$.email", "regex", ".+@.+")))));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/checks/sync",
                new HttpEntity<>(objectMapper.writeValueAsString(request), headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void seedHistoryRows(UUID checkId, String slug) {
        var projectId = projectRepository.findBySlug(slug).orElseThrow().getId();
        CheckEntity check = checkRepository.findById(checkId).orElseThrow();

        RunEntity runA = new RunEntity();
        runA.setProjectId(projectId);
        runA.setEnvironment("local");
        runA.setStatus(RunStatus.PASSED);
        runA.setConfigHash("cfg");
        runA.setAttemptCount(0);
        runA = runRepository.save(runA);

        RunEntity runB = new RunEntity();
        runB.setProjectId(projectId);
        runB.setEnvironment("local");
        runB.setStatus(RunStatus.FAILED);
        runB.setConfigHash("cfg");
        runB.setAttemptCount(0);
        runB = runRepository.save(runB);

        CheckResultEntity resultA = new CheckResultEntity();
        resultA.setRunId(runA.getId());
        resultA.setCheckId(check.getId());
        resultA.setCheckKey(check.getKey());
        resultA.setCheckName(check.getName());
        resultA.setStatus(CheckResultStatus.PASSED);
        resultA.setActualStatus(200);
        resultA.setLatencyMs(10L);
        checkResultRepository.save(resultA);

        CheckResultEntity resultB = new CheckResultEntity();
        resultB.setRunId(runB.getId());
        resultB.setCheckId(check.getId());
        resultB.setCheckKey(check.getKey());
        resultB.setCheckName(check.getName());
        resultB.setStatus(CheckResultStatus.FAILED);
        resultB.setActualStatus(500);
        resultB.setLatencyMs(20L);
        checkResultRepository.save(resultB);
    }
}
