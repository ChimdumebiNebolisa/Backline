package dev.backline.api.service;

import dev.backline.api.exception.ConflictException;
import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.repository.ProjectRepository;
import dev.backline.api.persistence.repository.RunEventRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.api.support.PostgresTestBase;
import dev.backline.core.run.RunEventType;
import dev.backline.core.run.RunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for run cancellation (audit finding F-003): queued/running runs can be
 * cancelled, terminal runs are immutable, and cancelling a running run releases the claim so
 * the worker's fenced finalize can never resurrect it.
 */
class RunCancellationTest extends PostgresTestBase {

    @Autowired
    private RunService runService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private RunEventRepository runEventRepository;

    @Test
    void cancelsQueuedRunAndRecordsEvent() {
        UUID projectId = saveProject();
        var run = runService.submit(new dev.backline.core.api.dto.CreateRunRequest(
                slugOf(projectId), "local", "cfg", null, "test"));

        var cancelled = runService.cancel(UUID.fromString(run.id()));

        assertThat(cancelled.status()).isEqualTo(RunStatus.CANCELLED);
        assertThat(runRepository.findById(UUID.fromString(run.id())).orElseThrow().getStatus())
                .isEqualTo(RunStatus.CANCELLED);
        Long events = runEventRepository.findByRunIdOrderByCreatedAtAsc(UUID.fromString(run.id())).stream()
                .filter(e -> e.getEventType().equals(RunEventType.CANCELLED.name()))
                .count();
        assertThat(events).isEqualTo(1L);
    }

    @Test
    void cancelReleasesRunningClaimOwnership() {
        UUID projectId = saveProject();
        RunEntity running = saveRun(projectId, RunStatus.RUNNING);
        running.setLockedBy("worker-a");
        running.setLockedAt(Instant.now());
        runRepository.save(running);

        var cancelled = runService.cancel(running.getId());

        assertThat(cancelled.status()).isEqualTo(RunStatus.CANCELLED);
        RunEntity row = runRepository.findById(running.getId()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(RunStatus.CANCELLED);
        assertThat(row.getLockedBy()).isNull();
        assertThat(row.getLockedAt()).isNull();
        assertThat(row.getTimeoutAt()).isNull();
        assertThat(row.getFinishedAt()).isNotNull();
    }

    @Test
    void cancelTerminalRunConflicts() {
        UUID projectId = saveProject();
        RunEntity failed = saveRun(projectId, RunStatus.FAILED);

        assertThatThrownBy(() -> runService.cancel(failed.getId()))
                .isInstanceOf(ConflictException.class);
        assertThat(runRepository.findById(failed.getId()).orElseThrow().getStatus())
                .isEqualTo(RunStatus.FAILED);
    }

    private UUID saveProject() {
        ProjectEntity project = new ProjectEntity();
        String slug = "cancel-" + UUID.randomUUID().toString().substring(0, 8);
        project.setSlug(slug);
        project.setName(slug);
        return projectRepository.save(project).getId();
    }

    private String slugOf(UUID projectId) {
        return projectRepository.findById(projectId).orElseThrow().getSlug();
    }

    private RunEntity saveRun(UUID projectId, RunStatus status) {
        RunEntity run = new RunEntity();
        run.setProjectId(projectId);
        run.setEnvironment("local");
        run.setStatus(status);
        run.setConfigHash("cfg");
        run.setAttemptCount(status == RunStatus.RUNNING ? 1 : 0);
        return runRepository.save(run);
    }
}
