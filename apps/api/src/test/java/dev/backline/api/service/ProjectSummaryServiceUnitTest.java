package dev.backline.api.service;

import dev.backline.api.exception.NotFoundException;
import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.repository.ProjectRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.core.run.RunStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectSummaryServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private RunRepository runRepository;

    @InjectMocks
    private ProjectSummaryService projectSummaryService;

    @Test
    void summarize_throwsNotFoundForMissingProject() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> projectSummaryService.summarize(projectId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void summarize_returnsCountsAndLastRun() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setSlug("demo");
        project.setName("Demo");
        RunEntity last = new RunEntity();
        last.setId(UUID.randomUUID());
        last.setProjectId(projectId);
        last.setEnvironment("local");
        last.setStatus(RunStatus.FAILED);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(runRepository.countByProjectId(projectId)).thenReturn(3L);
        when(runRepository.countByProjectIdAndStatus(projectId, RunStatus.PASSED)).thenReturn(1L);
        when(runRepository.countByProjectIdAndStatus(projectId, RunStatus.FAILED)).thenReturn(1L);
        when(runRepository.countByProjectIdAndStatus(projectId, RunStatus.ERROR)).thenReturn(1L);
        when(runRepository.findFirstByProjectIdOrderByQueuedAtDesc(projectId)).thenReturn(Optional.of(last));

        var summary = projectSummaryService.summarize(projectId);
        assertThat(summary.totalRuns()).isEqualTo(3L);
        assertThat(summary.lastRun()).isNotNull();
        assertThat(summary.project().slug()).isEqualTo("demo");
    }
}
