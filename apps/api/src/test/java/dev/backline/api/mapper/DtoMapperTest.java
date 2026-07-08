package dev.backline.api.mapper;

import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.entity.RunEventEntity;
import dev.backline.core.run.RunEventType;
import dev.backline.core.run.RunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DtoMapperTest {

    @Test
    void runMapper_mapsAllTimestampsAndIdempotencyKey() {
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant queued = Instant.parse("2024-01-01T00:00:00Z");
        RunEntity run = new RunEntity();
        run.setId(id);
        run.setProjectId(projectId);
        run.setEnvironment("local");
        run.setStatus(RunStatus.QUEUED);
        run.setConfigHash("hash");
        run.setSource("cli");
        run.setIdempotencyKey("idem-1");
        run.setQueuedAt(queued);

        var dto = RunMapper.toDto(run);
        assertThat(dto.id()).isEqualTo(id.toString());
        assertThat(dto.projectId()).isEqualTo(projectId.toString());
        assertThat(dto.idempotencyKey()).isEqualTo("idem-1");
        assertThat(dto.queuedAt()).isEqualTo(queued);
    }

    @Test
    void runEventMapper_parsesEventTypeEnum() {
        UUID runId = UUID.randomUUID();
        RunEventEntity event = new RunEventEntity();
        event.setId(UUID.randomUUID());
        event.setRunId(runId);
        event.setEventType(RunEventType.SUBMITTED.name());
        event.setMessage("queued");
        event.setCreatedAt(Instant.parse("2024-01-02T00:00:00Z"));

        var dto = RunEventMapper.toDto(event);
        assertThat(dto.type()).isEqualTo(RunEventType.SUBMITTED);
        assertThat(dto.runId()).isEqualTo(runId.toString());
    }

    @Test
    void projectMapper_mapsCreatedAndUpdatedAt() {
        Instant created = Instant.parse("2024-01-03T00:00:00Z");
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setSlug("sample");
        project.setName("Sample");
        project.setCreatedAt(created);
        project.setUpdatedAt(created);

        var dto = ProjectMapper.toDto(project);
        assertThat(dto.slug()).isEqualTo("sample");
        assertThat(dto.createdAt()).isEqualTo(created);
    }
}
