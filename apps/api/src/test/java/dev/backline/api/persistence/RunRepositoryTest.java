package dev.backline.api.persistence;

import dev.backline.api.support.PostgresTestBase;
import static org.assertj.core.api.Assertions.assertThat;

import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.repository.ProjectRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.core.run.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RunRepositoryTest extends PostgresTestBase {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RunRepository runRepository;

    @Test
    void findByIdempotencyKey() {
        ProjectEntity project = persistProject();
        RunEntity run = newRun(project.getId(), RunStatus.QUEUED, "idem-x", Instant.now().minusSeconds(60));
        runRepository.saveAndFlush(run);

        assertThat(runRepository.findByIdempotencyKey("idem-x")).isPresent().get().isEqualTo(run);
    }

    @Test
    void findPreviousCompletedRunOrdersByFinishedAtAndExcludesRunId() {
        ProjectEntity project = persistProject();
        String env = "local";

        RunEntity current = newRun(project.getId(), RunStatus.RUNNING, null, Instant.now());
        runRepository.saveAndFlush(current);

        RunEntity older =
                newCompletedRun(project.getId(), env, RunStatus.PASSED, Instant.parse("2024-01-01T00:00:00Z"));
        RunEntity newer =
                newCompletedRun(project.getId(), env, RunStatus.FAILED, Instant.parse("2024-02-01T00:00:00Z"));
        runRepository.saveAndFlush(older);
        runRepository.saveAndFlush(newer);

        List<RunEntity> previous =
                runRepository.findPreviousCompletedRun(
                        project.getId(), env, current.getId(), current.getQueuedAt(), PageRequest.of(0, 1));

        assertThat(previous).hasSize(1);
        assertThat(previous.getFirst().getId()).isEqualTo(newer.getId());
        assertThat(previous.getFirst().getStatus()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void findPreviousCompletedRunExcludesRunsQueuedAfterCurrent() {
        ProjectEntity project = persistProject();
        String env = "local";

        RunEntity current = newRun(project.getId(), RunStatus.RUNNING, null, Instant.parse("2024-01-15T00:00:00Z"));
        runRepository.saveAndFlush(current);

        RunEntity before =
                newCompletedRun(project.getId(), env, RunStatus.PASSED, Instant.parse("2024-01-10T00:00:00Z"));
        // Queued after current but finished later; must not be selected as the baseline.
        RunEntity queuedAfter =
                newCompletedRun(project.getId(), env, RunStatus.FAILED, Instant.parse("2024-01-20T00:00:00Z"));
        runRepository.saveAndFlush(before);
        runRepository.saveAndFlush(queuedAfter);

        List<RunEntity> previous =
                runRepository.findPreviousCompletedRun(
                        project.getId(), env, current.getId(), current.getQueuedAt(), PageRequest.of(0, 1));

        assertThat(previous).hasSize(1);
        assertThat(previous.getFirst().getId()).isEqualTo(before.getId());
    }

    @Test
    void findFirstByProjectIdOrderByQueuedAtDescAndCounts() {
        ProjectEntity project = persistProject();

        RunEntity first = newRun(project.getId(), RunStatus.QUEUED, null, Instant.now().minusSeconds(10));
        RunEntity second = newRun(project.getId(), RunStatus.PASSED, null, Instant.now());
        runRepository.saveAndFlush(first);
        runRepository.saveAndFlush(second);

        assertThat(runRepository.findFirstByProjectIdOrderByQueuedAtDesc(project.getId()))
                .isPresent()
                .get()
                .isEqualTo(second);

        assertThat(runRepository.countByProjectId(project.getId())).isEqualTo(2);
        assertThat(runRepository.countByProjectIdAndStatus(project.getId(), RunStatus.QUEUED)).isEqualTo(1);
        assertThat(runRepository.countByProjectIdAndStatus(project.getId(), RunStatus.PASSED)).isEqualTo(1);
    }

    private ProjectEntity persistProject() {
        ProjectEntity p = new ProjectEntity();
        p.setSlug("slug-" + UUID.randomUUID());
        p.setName("P");
        return projectRepository.saveAndFlush(p);
    }

    private static RunEntity newRun(UUID projectId, RunStatus status, String idempotencyKey, Instant queuedAt) {
        RunEntity r = new RunEntity();
        r.setProjectId(projectId);
        r.setEnvironment("local");
        r.setStatus(status);
        r.setConfigHash("cfg");
        r.setIdempotencyKey(idempotencyKey);
        r.setQueuedAt(queuedAt);
        return r;
    }

    private static RunEntity newCompletedRun(UUID projectId, String env, RunStatus status, Instant finishedAt) {
        RunEntity r = new RunEntity();
        r.setProjectId(projectId);
        r.setEnvironment(env);
        r.setStatus(status);
        r.setConfigHash("cfg");
        r.setQueuedAt(finishedAt.minusSeconds(5));
        r.setStartedAt(finishedAt.minusSeconds(4));
        r.setFinishedAt(finishedAt);
        return r;
    }
}
