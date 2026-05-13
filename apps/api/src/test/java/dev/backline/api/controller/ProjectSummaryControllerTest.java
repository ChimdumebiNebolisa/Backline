package dev.backline.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.repository.ProjectRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.api.support.PostgresTestBase;
import dev.backline.core.run.RunStatus;
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

class ProjectSummaryControllerTest extends PostgresTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RunRepository runRepository;

    @Test
    void summaryCountsMatch() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String slug = "sum-" + UUID.randomUUID().toString().substring(0, 8);
        restTemplate.postForEntity(
                "/api/projects",
                new HttpEntity<>(objectMapper.writeValueAsString(Map.of("slug", slug, "name", "S")), headers),
                String.class);

        ProjectEntity project = projectRepository.findBySlug(slug).orElseThrow();

        runRepository.save(run(project, RunStatus.PASSED));
        runRepository.save(run(project, RunStatus.PASSED));
        runRepository.save(run(project, RunStatus.FAILED));
        runRepository.save(run(project, RunStatus.ERROR));
        runRepository.save(run(project, RunStatus.QUEUED));

        ResponseEntity<String> res =
                restTemplate.getForEntity("/api/projects/" + project.getId() + "/summary", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = objectMapper.readTree(res.getBody()).path("data");
        assertThat(data.path("totalRuns").asLong()).isEqualTo(5);
        assertThat(data.path("passedRuns").asLong()).isEqualTo(2);
        assertThat(data.path("failedRuns").asLong()).isEqualTo(1);
        assertThat(data.path("erroredRuns").asLong()).isEqualTo(1);
    }

    private static RunEntity run(ProjectEntity project, RunStatus status) {
        RunEntity r = new RunEntity();
        r.setProjectId(project.getId());
        r.setEnvironment("local");
        r.setStatus(status);
        r.setConfigHash("h");
        r.setAttemptCount(0);
        return r;
    }
}
