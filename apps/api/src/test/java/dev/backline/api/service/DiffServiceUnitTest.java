package dev.backline.api.service;

import dev.backline.api.exception.NotFoundException;
import dev.backline.api.exception.ValidationFailedException;
import dev.backline.api.persistence.entity.CheckResultEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.repository.CheckResultRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.core.api.dto.DiffBaselineStrategy;
import dev.backline.core.api.dto.RunDiffChangeType;
import dev.backline.core.check.CheckResultStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.Collections;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiffServiceUnitTest {

    @Mock
    private RunRepository runRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @InjectMocks
    private DiffService diffService;

    @Test
    void latencyChanged_nullEitherSide_returnsFalse() {
        assertThat(DiffService.latencyChanged(null, 200L)).isFalse();
        assertThat(DiffService.latencyChanged(100L, null)).isFalse();
    }

    @Test
    void latencyChanged_absoluteThresholdOver100ms() {
        assertThat(DiffService.latencyChanged(100L, 250L)).isTrue();
    }

    @Test
    void latencyChanged_relativeThresholdOver50Percent() {
        assertThat(DiffService.latencyChanged(200L, 320L)).isTrue();
    }

    @Test
    void latencyChanged_previousZeroCurrentOver100() {
        assertThat(DiffService.latencyChanged(0L, 150L)).isTrue();
    }

    @Test
    void computeDiff_fixedRunMissingId_throwsValidation() {
        UUID runId = UUID.randomUUID();
        RunEntity current = run(runId, UUID.randomUUID(), "local");
        when(runRepository.findById(runId)).thenReturn(Optional.of(current));
        when(checkResultRepository.findByRunId(runId)).thenReturn(List.of());

        assertThatThrownBy(() -> diffService.computeDiff(runId, DiffBaselineStrategy.FIXED_RUN, null))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void computeDiff_fixedRunSameAsCurrent_throwsValidation() {
        UUID runId = UUID.randomUUID();
        RunEntity current = run(runId, UUID.randomUUID(), "local");
        when(runRepository.findById(runId)).thenReturn(Optional.of(current));
        when(checkResultRepository.findByRunId(runId)).thenReturn(List.of());

        assertThatThrownBy(() -> diffService.computeDiff(runId, DiffBaselineStrategy.FIXED_RUN, runId))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void computeDiff_fixedRunWrongProject_throwsValidation() {
        UUID runId = UUID.randomUUID();
        UUID fixedId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        RunEntity current = run(runId, projectId, "local");
        RunEntity fixed = run(fixedId, UUID.randomUUID(), "local");
        when(runRepository.findById(runId)).thenReturn(Optional.of(current));
        when(runRepository.findById(fixedId)).thenReturn(Optional.of(fixed));
        when(checkResultRepository.findByRunId(runId)).thenReturn(List.of());

        assertThatThrownBy(() -> diffService.computeDiff(runId, DiffBaselineStrategy.FIXED_RUN, fixedId))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void computeDiff_fixedRunNotFound_throwsNotFound() {
        UUID runId = UUID.randomUUID();
        UUID fixedId = UUID.randomUUID();
        RunEntity current = run(runId, UUID.randomUUID(), "local");
        when(runRepository.findById(runId)).thenReturn(Optional.of(current));
        when(runRepository.findById(fixedId)).thenReturn(Optional.empty());
        when(checkResultRepository.findByRunId(runId)).thenReturn(List.of());

        assertThatThrownBy(() -> diffService.computeDiff(runId, DiffBaselineStrategy.FIXED_RUN, fixedId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void computeDiff_noPrevious_skippedResult_mapsToStillFailing() {
        UUID runId = UUID.randomUUID();
        RunEntity current = run(runId, UUID.randomUUID(), "local");
        CheckResultEntity skipped = result(runId, "sk", CheckResultStatus.SKIPPED);
        when(runRepository.findById(runId)).thenReturn(Optional.of(current));
        when(checkResultRepository.findByRunId(runId)).thenReturn(List.of(skipped));
        when(runRepository.findPreviousCompletedRun(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        var diff = diffService.computeDiff(runId);
        assertThat(diff.entries()).singleElement()
                .satisfies(e -> assertThat(e.changeType()).isEqualTo(RunDiffChangeType.STILL_FAILING));
    }

    @Test
    void computeDiff_bothFailingWithStatusCodeChange_reportsStatusCodeChanged() {
        UUID runId = UUID.randomUUID();
        UUID prevId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        RunEntity current = run(runId, projectId, "local");
        RunEntity previous = run(prevId, projectId, "local");

        CheckResultEntity prevResult = result(prevId, "api", CheckResultStatus.FAILED);
        prevResult.setActualStatus(500);
        CheckResultEntity curResult = result(runId, "api", CheckResultStatus.FAILED);
        curResult.setActualStatus(404);

        when(runRepository.findById(runId)).thenReturn(Optional.of(current));
        when(checkResultRepository.findByRunId(runId)).thenReturn(List.of(curResult));
        when(checkResultRepository.findByRunId(prevId)).thenReturn(List.of(prevResult));
        when(runRepository.findPreviousCompletedRun(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(previous));

        var diff = diffService.computeDiff(runId);
        assertThat(diff.entries()).singleElement()
                .satisfies(e -> assertThat(e.changeType()).isEqualTo(RunDiffChangeType.STATUS_CODE_CHANGED));
    }

    @Test
    void computeDiff_bothFailingWithAssertionChange_reportsAssertionChanged() {
        UUID runId = UUID.randomUUID();
        UUID prevId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        RunEntity current = run(runId, projectId, "local");
        RunEntity previous = run(prevId, projectId, "local");

        CheckResultEntity prevResult = result(prevId, "api", CheckResultStatus.FAILED);
        prevResult.setActualStatus(200);
        prevResult.setAssertionsJson("[{\"path\":\"$.id\",\"passed\":true}]");
        CheckResultEntity curResult = result(runId, "api", CheckResultStatus.FAILED);
        curResult.setActualStatus(200);
        curResult.setAssertionsJson("[{\"path\":\"$.id\",\"passed\":false}]");

        when(runRepository.findById(runId)).thenReturn(Optional.of(current));
        when(checkResultRepository.findByRunId(runId)).thenReturn(List.of(curResult));
        when(checkResultRepository.findByRunId(prevId)).thenReturn(List.of(prevResult));
        when(runRepository.findPreviousCompletedRun(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(previous));

        var diff = diffService.computeDiff(runId);
        assertThat(diff.entries()).singleElement()
                .satisfies(e -> assertThat(e.changeType()).isEqualTo(RunDiffChangeType.ASSERTION_CHANGED));
    }

    @Test
    void computeDiff_fixedRunWrongEnvironment_throwsValidation() {
        UUID runId = UUID.randomUUID();
        UUID fixedId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        RunEntity current = run(runId, projectId, "local");
        RunEntity fixed = run(fixedId, projectId, "staging");
        when(runRepository.findById(runId)).thenReturn(Optional.of(current));
        when(runRepository.findById(fixedId)).thenReturn(Optional.of(fixed));
        when(checkResultRepository.findByRunId(runId)).thenReturn(List.of());

        assertThatThrownBy(() -> diffService.computeDiff(runId, DiffBaselineStrategy.FIXED_RUN, fixedId))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void computeDiff_runNotFound_throwsNotFound() {
        UUID runId = UUID.randomUUID();
        when(runRepository.findById(runId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> diffService.computeDiff(runId))
                .isInstanceOf(NotFoundException.class);
    }

    private static RunEntity run(UUID id, UUID projectId, String environment) {
        RunEntity run = new RunEntity();
        run.setId(id);
        run.setProjectId(projectId);
        run.setEnvironment(environment);
        return run;
    }

    private static CheckResultEntity result(UUID runId, String key, CheckResultStatus status) {
        CheckResultEntity entity = new CheckResultEntity();
        entity.setId(UUID.randomUUID());
        entity.setRunId(runId);
        entity.setCheckKey(key);
        entity.setCheckName(key);
        entity.setStatus(status);
        return entity;
    }
}
