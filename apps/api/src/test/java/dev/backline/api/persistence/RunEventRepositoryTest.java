package dev.backline.api.persistence;

import dev.backline.api.support.PostgresTestBase;
import static org.assertj.core.api.Assertions.assertThat;

import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.entity.RunEventEntity;
import dev.backline.api.persistence.repository.ProjectRepository;
import dev.backline.api.persistence.repository.RunEventRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.core.run.RunEventType;
import dev.backline.core.run.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RunEventRepositoryTest extends PostgresTestBase {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private RunEventRepository runEventRepository;

    @Test
    void findByRunIdOrderByCreatedAtAsc() {
        ProjectEntity project = persistProject();
        RunEntity run = persistRun(project.getId());

        RunEventEntity e1 = event(run.getId(), RunEventType.SUBMITTED.name(), Instant.parse("2024-01-02T00:00:00Z"));
        RunEventEntity e2 = event(run.getId(), RunEventType.CLAIMED.name(), Instant.parse("2024-01-01T00:00:00Z"));
        runEventRepository.saveAndFlush(e2);
        runEventRepository.saveAndFlush(e1);

        List<RunEventEntity> ordered = runEventRepository.findByRunIdOrderByCreatedAtAsc(run.getId());

        assertThat(ordered).extracting(RunEventEntity::getEventType).containsExactly("CLAIMED", "SUBMITTED");
    }

    private ProjectEntity persistProject() {
        ProjectEntity p = new ProjectEntity();
        p.setSlug("slug-" + UUID.randomUUID());
        p.setName("P");
        return projectRepository.saveAndFlush(p);
    }

    private RunEntity persistRun(UUID projectId) {
        RunEntity r = new RunEntity();
        r.setProjectId(projectId);
        r.setEnvironment("local");
        r.setStatus(RunStatus.QUEUED);
        r.setConfigHash("cfg");
        return runRepository.saveAndFlush(r);
    }

    private static RunEventEntity event(UUID runId, String type, Instant createdAt) {
        RunEventEntity e = new RunEventEntity();
        e.setRunId(runId);
        e.setEventType(type);
        e.setMessage("m");
        e.setCreatedAt(createdAt);
        return e;
    }
}
