package dev.backline.api.service;

import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.repository.ProjectRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.api.support.PostgresTestBase;
import dev.backline.core.api.dto.CreateRunRequest;
import dev.backline.core.run.RunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RunServiceTest extends PostgresTestBase {

    @Autowired
    private RunService runService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RunRepository runRepository;

    @Test
    void submitReusesRunForIdempotencyKey() {
        ProjectEntity project = saveProject("idem-" + UUID.randomUUID().toString().substring(0, 8));

        var first = runService.submit(new CreateRunRequest(project.getSlug(), "local", "cfg-1", "same-key", "test"));
        var second = runService.submit(new CreateRunRequest(project.getSlug(), "local", "cfg-1", "same-key", "test"));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(runRepository.count()).isEqualTo(1);
    }

    @Test
    void listRespectsFilterAndTrueOffset() {
        ProjectEntity projectA = saveProject("runs-a-" + UUID.randomUUID().toString().substring(0, 6));
        ProjectEntity projectB = saveProject("runs-b-" + UUID.randomUUID().toString().substring(0, 6));

        RunEntity a1 = saveRun(projectA.getId(), "local", RunStatus.QUEUED, Instant.parse("2024-01-01T00:00:00Z"));
        RunEntity a2 = saveRun(projectA.getId(), "local", RunStatus.FAILED, Instant.parse("2024-01-02T00:00:00Z"));
        RunEntity b1 = saveRun(projectB.getId(), "local", RunStatus.QUEUED, Instant.parse("2024-01-03T00:00:00Z"));

        var filtered = runService.list(new RunFilter(projectA.getSlug(), "local", RunStatus.QUEUED, null, null), 10, 0);
        assertThat(filtered.getContent()).extracting("id").containsExactly(a1.getId().toString());

        var allRuns = runService.list(new RunFilter(null, "local", null, null, null), 1, 0);
        var offsetRuns = runService.list(new RunFilter(null, "local", null, null, null), 1, 1);
        assertThat(allRuns.getContent()).hasSize(1);
        assertThat(offsetRuns.getContent()).hasSize(1);
        assertThat(offsetRuns.getContent().getFirst().id()).isNotEqualTo(allRuns.getContent().getFirst().id());
        assertThat(runService.list(new RunFilter(projectB.getSlug(), "local", null, null, null), 10, 0)
                .getContent())
                .extracting("id")
                .containsExactly(b1.getId().toString());
    }

    private ProjectEntity saveProject(String slug) {
        ProjectEntity project = new ProjectEntity();
        project.setSlug(slug);
        project.setName(slug);
        return projectRepository.save(project);
    }

    private RunEntity saveRun(UUID projectId, String environment, RunStatus status, Instant queuedAt) {
        RunEntity run = new RunEntity();
        run.setProjectId(projectId);
        run.setEnvironment(environment);
        run.setStatus(status);
        run.setConfigHash("cfg");
        run.setQueuedAt(queuedAt);
        run.setAttemptCount(0);
        return runRepository.save(run);
    }
}
