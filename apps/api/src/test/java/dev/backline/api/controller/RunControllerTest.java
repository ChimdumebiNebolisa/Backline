package dev.backline.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.persistence.entity.CheckEntity;
import dev.backline.api.persistence.entity.CheckResultEntity;
import dev.backline.api.persistence.entity.ProjectEntity;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RunControllerTest extends PostgresTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private CheckRepository checkRepository;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private CheckResultRepository checkResultRepository;

    @Test
    void submitIdempotentListFilterResultsAndDiff() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String slug = "run-" + UUID.randomUUID().toString().substring(0, 8);

        restTemplate.postForEntity(
                "/api/projects",
                new HttpEntity<>(
                        objectMapper.writeValueAsString(Map.of("slug", slug, "name", "Run Proj")), headers),
                String.class);

        Map<String, Object> sync = Map.of(
                "projectSlug",
                slug,
                "checks",
                List.of(Map.of(
                        "key",
                        "k1",
                        "name",
                        "K1",
                        "method",
                        "GET",
                        "url",
                        "http://localhost:8081/health",
                        "expectedStatus",
                        200)));
        ResponseEntity<String> syncRes = restTemplate.postForEntity(
                "/api/checks/sync", new HttpEntity<>(objectMapper.writeValueAsString(sync), headers), String.class);
        String checkId =
                objectMapper.readTree(syncRes.getBody()).path("data").get(0).path("id").asText();

        String runBody = objectMapper.writeValueAsString(Map.of(
                "projectSlug",
                slug,
                "environment",
                "local",
                "configHash",
                "abc",
                "idempotencyKey",
                "idem-1",
                "source",
                "test"));
        ResponseEntity<String> run1 =
                restTemplate.postForEntity("/api/runs", new HttpEntity<>(runBody, headers), String.class);
        assertThat(run1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String runId = objectMapper.readTree(run1.getBody()).path("data").path("id").asText();

        ResponseEntity<String> run2 =
                restTemplate.postForEntity("/api/runs", new HttpEntity<>(runBody, headers), String.class);
        assertThat(run2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(objectMapper.readTree(run2.getBody()).path("data").path("id").asText()).isEqualTo(runId);

        String runBody2 = objectMapper.writeValueAsString(Map.of(
                "projectSlug",
                slug,
                "environment",
                "local",
                "configHash",
                "abc",
                "idempotencyKey",
                "idem-2",
                "source",
                "test"));
        restTemplate.postForEntity("/api/runs", new HttpEntity<>(runBody2, headers), String.class);

        ResponseEntity<String> list = restTemplate.getForEntity(
                "/api/runs?projectSlug=" + slug + "&status=QUEUED&limit=10&offset=0", String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode listData = objectMapper.readTree(list.getBody()).path("data");
        assertThat(listData.isArray()).isTrue();
        assertThat(listData.size()).isGreaterThanOrEqualTo(1);

        ResponseEntity<String> listOffset = restTemplate.getForEntity(
                "/api/runs?projectSlug=" + slug + "&status=QUEUED&limit=1&offset=1", String.class);
        var firstRunPage = objectMapper.readTree(list.getBody()).path("data");
        var offsetPage = objectMapper.readTree(listOffset.getBody()).path("data");
        if (firstRunPage.size() > 1 && offsetPage.size() > 0) {
            assertThat(firstRunPage.get(0).path("id").asText()).isNotEqualTo(offsetPage.get(0).path("id").asText());
        }

        ResponseEntity<String> one = restTemplate.getForEntity("/api/runs/" + runId, String.class);
        assertThat(one.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> results = restTemplate.getForEntity("/api/runs/" + runId + "/results", String.class);
        assertThat(results.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(results.getBody()).path("data").size()).isZero();

        seedCompletedRunForDiff(slug, UUID.fromString(checkId), UUID.fromString(runId));

        ResponseEntity<String> diff = restTemplate.getForEntity("/api/runs/" + runId + "/diff", String.class);
        assertThat(diff.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(diff.getBody()).path("data").path("entries").isArray()).isTrue();

        ResponseEntity<String> diffLastPassed =
                restTemplate.getForEntity("/api/runs/" + runId + "/diff?baseline=LAST_PASSED", String.class);
        assertThat(diffLastPassed.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> diffMissingFixed =
                restTemplate.getForEntity("/api/runs/" + runId + "/diff?baseline=FIXED_RUN", String.class);
        assertThat(diffMissingFixed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(diffMissingFixed.getBody()).path("error").path("field").asText())
                .isEqualTo("fixedRunId");
    }

    void seedCompletedRunForDiff(String slug, UUID checkUuid, UUID currentRunId) {
        ProjectEntity project = projectRepository.findBySlug(slug).orElseThrow();
        CheckEntity check = checkRepository.findById(checkUuid).orElseThrow();

        RunEntity previous = new RunEntity();
        previous.setProjectId(project.getId());
        previous.setEnvironment("local");
        previous.setStatus(RunStatus.PASSED);
        previous.setConfigHash("prev");
        previous.setQueuedAt(Instant.parse("2020-01-01T00:00:00Z"));
        previous.setFinishedAt(Instant.parse("2020-01-01T00:01:00Z"));
        previous.setAttemptCount(0);
        previous = runRepository.save(previous);

        CheckResultEntity pr1 = new CheckResultEntity();
        pr1.setRunId(previous.getId());
        pr1.setCheckId(check.getId());
        pr1.setCheckKey("k1");
        pr1.setCheckName("K1");
        pr1.setStatus(CheckResultStatus.PASSED);
        pr1.setActualStatus(200);
        pr1.setLatencyMs(50L);
        pr1.setCreatedAt(Instant.now());
        checkResultRepository.save(pr1);

        RunEntity current = runRepository.findById(currentRunId).orElseThrow();
        current.setQueuedAt(Instant.parse("2020-02-01T00:00:00Z"));
        runRepository.save(current);

        CheckResultEntity cr1 = new CheckResultEntity();
        cr1.setRunId(current.getId());
        cr1.setCheckId(check.getId());
        cr1.setCheckKey("k1");
        cr1.setCheckName("K1");
        cr1.setStatus(CheckResultStatus.FAILED);
        cr1.setActualStatus(500);
        cr1.setLatencyMs(200L);
        cr1.setCreatedAt(Instant.now());
        checkResultRepository.save(cr1);
    }
}
