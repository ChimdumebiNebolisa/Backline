package dev.backline.api.service;

import dev.backline.api.exception.ValidationFailedException;
import dev.backline.api.persistence.entity.CheckResultEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.repository.CheckResultRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.core.api.dto.DiffBaselineStrategy;
import dev.backline.core.api.dto.RunDiffChangeType;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.check.CheckResultStatus;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property tests for DiffService baseline selection and classification (Q9).
 *
 * <p>Properties under test:
 * <ol>
 *   <li>latencyChanged is false when either latency is null</li>
 *   <li>latencyChanged is false when both latencies are equal</li>
 *   <li>latencyChanged is true when absolute delta exceeds 100ms</li>
 *   <li>latencyChanged is true when previous &gt; 0 and relative delta exceeds 50%</li>
 *   <li>latencyChanged is false when both absolute and relative thresholds are unmet</li>
 *   <li>FIXED_RUN without fixedRunId always rejects</li>
 *   <li>FIXED_RUN rejects baselines from a different project or environment</li>
 *   <li>PREVIOUS_COMPLETED with no candidate yields a null previousRunId</li>
 *   <li>LAST_PASSED uses the passed-run baseline when one exists</li>
 *   <li>No-previous status mapping: PASSED→NEWLY_PASSING, FAILED/ERROR→NEWLY_FAILING, SKIPPED→STILL_FAILING</li>
 *   <li>Status transition to PASSED always classifies as NEWLY_PASSING</li>
 *   <li>Same status with differing HTTP status codes classifies as STATUS_CODE_CHANGED</li>
 *   <li>Diff entries are always sorted by checkKey</li>
 * </ol>
 */
class DiffServicePropertiesTest {

    @Property
    void latencyChanged_nullEitherSide_isFalse(@ForAll @LongRange(min = 0, max = 10_000) long latencyMs) {
        assertThat(DiffService.latencyChanged(null, latencyMs)).isFalse();
        assertThat(DiffService.latencyChanged(latencyMs, null)).isFalse();
    }

    @Property
    void latencyChanged_equalValues_isFalse(@ForAll @LongRange(min = 0, max = 10_000) long latencyMs) {
        assertThat(DiffService.latencyChanged(latencyMs, latencyMs)).isFalse();
    }

    @Property
    void latencyChanged_absoluteDeltaOver100_isTrue(
            @ForAll @LongRange(min = 0, max = 5_000) long previousMs,
            @ForAll @LongRange(min = 101, max = 5_000) long absoluteDelta,
            @ForAll boolean increase) {
        long currentMs = increase ? previousMs + absoluteDelta : Math.max(0, previousMs - absoluteDelta);
        if (Math.abs(currentMs - previousMs) <= 100) {
            return;
        }
        assertThat(DiffService.latencyChanged(previousMs, currentMs)).isTrue();
    }

    @Property
    void latencyChanged_relativeDeltaOver50Percent_isTrue(
            @ForAll @LongRange(min = 1, max = 200) long previousMs,
            @ForAll @LongRange(min = 1, max = 200) long relativeSteps) {
        // Choose a current that violates relative (>50% of previous) while keeping abs delta <= 100 when possible.
        long minRelativeDelta = (previousMs / 2) + 1;
        long delta = Math.min(100, Math.max(minRelativeDelta, relativeSteps));
        if (delta * 2 <= previousMs) {
            return;
        }
        long currentMs = previousMs + delta;
        assertThat(DiffService.latencyChanged(previousMs, currentMs)).isTrue();
    }

    @Property
    void latencyChanged_withinBothThresholds_isFalse(
            @ForAll @LongRange(min = 0, max = 10_000) long previousMs,
            @ForAll @LongRange(min = 0, max = 100) long absoluteDelta,
            @ForAll boolean increase) {
        long currentMs = increase ? previousMs + absoluteDelta : Math.max(0, previousMs - absoluteDelta);
        long abs = Math.abs(currentMs - previousMs);
        if (abs > 100) {
            return;
        }
        if (previousMs > 0 && abs * 2 > previousMs) {
            return;
        }
        if (previousMs == 0 && currentMs > 100) {
            return;
        }
        assertThat(DiffService.latencyChanged(previousMs, currentMs)).isFalse();
    }

    @Property
    void fixedRun_nullId_alwaysRejects() {
        UUID runId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        RunEntity current = run(runId, projectId, "local");
        RunRepository runRepository = mock(RunRepository.class);
        CheckResultRepository checkResultRepository = mock(CheckResultRepository.class);
        when(runRepository.findById(runId)).thenReturn(Optional.of(current));
        when(checkResultRepository.findByRunId(runId)).thenReturn(List.of());
        DiffService diffService = new DiffService(runRepository, checkResultRepository);

        assertThatThrownBy(() -> diffService.computeDiff(runId, DiffBaselineStrategy.FIXED_RUN, null))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("fixedRunId");
    }

    @Property
    void fixedRun_rejectsDifferentProjectOrEnvironment(
            @ForAll boolean wrongProject, @ForAll boolean wrongEnvironment) {
        if (!wrongProject && !wrongEnvironment) {
            return;
        }
        UUID runId = UUID.randomUUID();
        UUID fixedId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        RunEntity current = run(runId, projectId, "local");
        RunEntity fixed = run(
                fixedId,
                wrongProject ? UUID.randomUUID() : projectId,
                wrongEnvironment ? "staging" : "local");
        RunRepository runRepository = mock(RunRepository.class);
        CheckResultRepository checkResultRepository = mock(CheckResultRepository.class);
        when(runRepository.findById(runId)).thenReturn(Optional.of(current));
        when(runRepository.findById(fixedId)).thenReturn(Optional.of(fixed));
        when(checkResultRepository.findByRunId(runId)).thenReturn(List.of());
        DiffService diffService = new DiffService(runRepository, checkResultRepository);

        assertThatThrownBy(() -> diffService.computeDiff(runId, DiffBaselineStrategy.FIXED_RUN, fixedId))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Property
    void previousCompleted_withNoCandidate_hasNullBaseline(@ForAll CheckResultStatus status) {
        UUID runId = UUID.randomUUID();
        RunEntity current = run(runId, UUID.randomUUID(), "local");
        CheckResultEntity result = result(runId, "k", status);
        RunRepository runRepository = mock(RunRepository.class);
        CheckResultRepository checkResultRepository = mock(CheckResultRepository.class);
        when(runRepository.findById(runId)).thenReturn(Optional.of(current));
        when(checkResultRepository.findByRunId(runId)).thenReturn(List.of(result));
        when(runRepository.findPreviousCompletedRun(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        DiffService diffService = new DiffService(runRepository, checkResultRepository);

        RunDiffDto diff = diffService.computeDiff(runId, DiffBaselineStrategy.PREVIOUS_COMPLETED, null);

        assertThat(diff.previousRunId()).isNull();
        assertThat(diff.entries()).singleElement().satisfies(entry -> {
            RunDiffChangeType expected = switch (status) {
                case PASSED -> RunDiffChangeType.NEWLY_PASSING;
                case FAILED, ERROR -> RunDiffChangeType.NEWLY_FAILING;
                case SKIPPED -> RunDiffChangeType.STILL_FAILING;
            };
            assertThat(entry.changeType()).isEqualTo(expected);
        });
    }

    @Property
    void lastPassed_usesPassedBaselineWhenPresent(@ForAll CheckResultStatus currentStatus) {
        UUID runId = UUID.randomUUID();
        UUID prevId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        RunEntity current = run(runId, projectId, "local");
        RunEntity previous = run(prevId, projectId, "local");
        CheckResultEntity curResult = result(runId, "api", currentStatus);
        curResult.setActualStatus(200);
        CheckResultEntity prevResult = result(prevId, "api", CheckResultStatus.PASSED);
        prevResult.setActualStatus(200);

        RunRepository runRepository = mock(RunRepository.class);
        CheckResultRepository checkResultRepository = mock(CheckResultRepository.class);
        when(runRepository.findById(runId)).thenReturn(Optional.of(current));
        when(checkResultRepository.findByRunId(runId)).thenReturn(List.of(curResult));
        when(checkResultRepository.findByRunId(prevId)).thenReturn(List.of(prevResult));
        when(runRepository.findPreviousPassedRun(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(previous));
        DiffService diffService = new DiffService(runRepository, checkResultRepository);

        RunDiffDto diff = diffService.computeDiff(runId, DiffBaselineStrategy.LAST_PASSED, null);

        assertThat(diff.previousRunId()).isEqualTo(prevId.toString());
        assertThat(diff.entries()).isNotEmpty();
    }

    @Property
    void statusTransitionToPassed_alwaysNewlyPassing(
            @ForAll CheckResultStatus previousStatus,
            @ForAll @LongRange(min = 0, max = 5_000) long previousLatency,
            @ForAll @LongRange(min = 0, max = 5_000) long currentLatency) {
        if (previousStatus == CheckResultStatus.PASSED) {
            return;
        }
        UUID runId = UUID.randomUUID();
        UUID prevId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        RunEntity current = run(runId, projectId, "local");
        RunEntity previous = run(prevId, projectId, "local");

        CheckResultEntity prevResult = result(prevId, "api", previousStatus);
        prevResult.setActualStatus(500);
        prevResult.setLatencyMs(previousLatency);
        prevResult.setAssertionsJson("[{\"path\":\"$.id\",\"passed\":false}]");
        CheckResultEntity curResult = result(runId, "api", CheckResultStatus.PASSED);
        curResult.setActualStatus(200);
        curResult.setLatencyMs(currentLatency);
        curResult.setAssertionsJson("[{\"path\":\"$.id\",\"passed\":true}]");

        DiffService diffService = serviceWithPrevious(runId, prevId, current, previous, curResult, prevResult);
        RunDiffDto diff = diffService.computeDiff(runId);

        assertThat(diff.entries()).singleElement()
                .satisfies(e -> assertThat(e.changeType()).isEqualTo(RunDiffChangeType.NEWLY_PASSING));
    }

    @Property
    void sameStatus_differentHttpCode_isStatusCodeChanged(
            @ForAll CheckResultStatus status,
            @ForAll @LongRange(min = 100, max = 599) int previousCode,
            @ForAll @LongRange(min = 100, max = 599) int currentCode) {
        if (previousCode == currentCode) {
            return;
        }
        UUID runId = UUID.randomUUID();
        UUID prevId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        RunEntity current = run(runId, projectId, "local");
        RunEntity previous = run(prevId, projectId, "local");

        CheckResultEntity prevResult = result(prevId, "api", status);
        prevResult.setActualStatus(previousCode);
        prevResult.setLatencyMs(100L);
        prevResult.setAssertionsJson("[{\"path\":\"$.id\",\"passed\":true}]");
        CheckResultEntity curResult = result(runId, "api", status);
        curResult.setActualStatus(currentCode);
        curResult.setLatencyMs(500L);
        curResult.setAssertionsJson("[{\"path\":\"$.id\",\"passed\":false}]");

        DiffService diffService = serviceWithPrevious(runId, prevId, current, previous, curResult, prevResult);
        RunDiffDto diff = diffService.computeDiff(runId);

        assertThat(diff.entries()).singleElement()
                .satisfies(e -> assertThat(e.changeType()).isEqualTo(RunDiffChangeType.STATUS_CODE_CHANGED));
    }

    @Property
    void entries_areSortedByCheckKey(
            @ForAll CheckResultStatus statusA, @ForAll CheckResultStatus statusB) {
        UUID runId = UUID.randomUUID();
        RunEntity current = run(runId, UUID.randomUUID(), "local");
        CheckResultEntity first = result(runId, "z-key", statusA);
        CheckResultEntity second = result(runId, "a-key", statusB);

        RunRepository runRepository = mock(RunRepository.class);
        CheckResultRepository checkResultRepository = mock(CheckResultRepository.class);
        when(runRepository.findById(runId)).thenReturn(Optional.of(current));
        when(checkResultRepository.findByRunId(runId)).thenReturn(List.of(first, second));
        when(runRepository.findPreviousCompletedRun(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        DiffService diffService = new DiffService(runRepository, checkResultRepository);

        RunDiffDto diff = diffService.computeDiff(runId);

        assertThat(diff.entries())
                .extracting(e -> e.checkKey())
                .isSortedAccordingTo(Comparator.naturalOrder());
    }

    private static DiffService serviceWithPrevious(
            UUID runId,
            UUID prevId,
            RunEntity current,
            RunEntity previous,
            CheckResultEntity curResult,
            CheckResultEntity prevResult) {
        RunRepository runRepository = mock(RunRepository.class);
        CheckResultRepository checkResultRepository = mock(CheckResultRepository.class);
        when(runRepository.findById(runId)).thenReturn(Optional.of(current));
        when(checkResultRepository.findByRunId(eq(runId))).thenReturn(List.of(curResult));
        when(checkResultRepository.findByRunId(eq(prevId))).thenReturn(List.of(prevResult));
        when(runRepository.findPreviousCompletedRun(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(previous));
        return new DiffService(runRepository, checkResultRepository);
    }

    private static RunEntity run(UUID id, UUID projectId, String environment) {
        RunEntity run = new RunEntity();
        run.setId(id);
        run.setProjectId(projectId);
        run.setEnvironment(environment);
        run.setQueuedAt(Instant.parse("2024-01-01T00:00:00Z"));
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
