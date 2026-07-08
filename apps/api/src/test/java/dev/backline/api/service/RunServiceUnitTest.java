package dev.backline.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.exception.NotFoundException;
import dev.backline.api.exception.ValidationFailedException;
import dev.backline.api.persistence.entity.CheckResultEntity;
import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.entity.RunEventEntity;
import dev.backline.api.persistence.repository.CheckResultRepository;
import dev.backline.api.persistence.repository.RunEventRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.core.api.dto.CreateRunRequest;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.run.RunEventType;
import dev.backline.core.run.RunStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunServiceUnitTest {

    @Mock
    private RunRepository runRepository;

    @Mock
    private RunEventRepository runEventRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RunService runService;

    @Test
    void submit_rejectsBlankProjectSlug() {
        assertThatThrownBy(() -> runService.submit(new CreateRunRequest(" ", "local", "hash", null, "cli")))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void submit_rejectsBlankEnvironment() {
        assertThatThrownBy(() -> runService.submit(new CreateRunRequest("proj", " ", "hash", null, "cli")))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void submit_rejectsBlankConfigHash() {
        assertThatThrownBy(() -> runService.submit(new CreateRunRequest("proj", "local", " ", null, "cli")))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void findById_throwsNotFoundForMissingRun() {
        UUID id = UUID.randomUUID();
        when(runRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> runService.findById(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void eventsForRun_returnsSubmittedEvent() {
        UUID runId = UUID.randomUUID();
        when(runRepository.existsById(runId)).thenReturn(true);
        RunEventEntity event = new RunEventEntity();
        event.setId(UUID.randomUUID());
        event.setRunId(runId);
        event.setEventType(RunEventType.SUBMITTED.name());
        event.setMessage("Run queued");
        when(runEventRepository.findByRunIdOrderByCreatedAtAsc(runId)).thenReturn(List.of(event));

        var events = runService.eventsForRun(runId);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().type()).isEqualTo(RunEventType.SUBMITTED);
    }

    @Test
    void eventsForRun_throwsWhenRunMissing() {
        UUID runId = UUID.randomUUID();
        when(runRepository.existsById(runId)).thenReturn(false);
        assertThatThrownBy(() -> runService.eventsForRun(runId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void submit_withoutIdempotencyKey_createsNewRunEachTime() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setSlug("proj");
        when(projectService.requireBySlug("proj")).thenReturn(project);
        when(runRepository.save(any(RunEntity.class))).thenAnswer(inv -> {
            RunEntity run = inv.getArgument(0);
            run.setId(UUID.randomUUID());
            return run;
        });

        var dto = runService.submit(new CreateRunRequest("proj", "local", "hash", null, "cli"));
        assertThat(dto.status()).isEqualTo(RunStatus.QUEUED);

        ArgumentCaptor<RunEventEntity> captor = ArgumentCaptor.forClass(RunEventEntity.class);
        verify(runEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(RunEventType.SUBMITTED.name());
    }

    @Test
    void resultsForRun_ordersByCheckKeyAndMapsAssertions() {
        UUID runId = UUID.randomUUID();
        when(runRepository.existsById(runId)).thenReturn(true);
        CheckResultEntity b = result(runId, "b");
        CheckResultEntity a = result(runId, "a");
        when(checkResultRepository.findByRunId(runId)).thenReturn(List.of(b, a));

        var results = runService.resultsForRun(runId);
        assertThat(results).extracting(r -> r.checkKey()).containsExactly("a", "b");
    }

    private static CheckResultEntity result(UUID runId, String key) {
        CheckResultEntity entity = new CheckResultEntity();
        entity.setId(UUID.randomUUID());
        entity.setRunId(runId);
        entity.setCheckKey(key);
        entity.setCheckName(key);
        entity.setStatus(CheckResultStatus.PASSED);
        return entity;
    }
}
