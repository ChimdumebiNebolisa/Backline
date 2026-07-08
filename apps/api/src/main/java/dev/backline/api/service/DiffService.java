package dev.backline.api.service;

import dev.backline.api.exception.NotFoundException;
import dev.backline.api.exception.ValidationFailedException;
import dev.backline.core.api.dto.DiffBaselineStrategy;
import dev.backline.api.persistence.entity.CheckResultEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.repository.CheckResultRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.core.api.dto.RunDiffChangeType;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.api.dto.RunDiffEntry;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.run.RunStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Computes regression diffs between a run and the chronologically prior completed run (same project and environment,
 * {@link RunStatus#PASSED} or {@link RunStatus#FAILED} with {@code finished_at} set, queued before the current run).
 *
 * <p>Latency changes are flagged when both sides have {@code latency_ms} and either the absolute delta exceeds 100ms
 * or the relative delta exceeds 50% of the baseline (non-zero previous latency).
 */
@Service
public class DiffService {

    private final RunRepository runRepository;
    private final CheckResultRepository checkResultRepository;

    public DiffService(RunRepository runRepository, CheckResultRepository checkResultRepository) {
        this.runRepository = runRepository;
        this.checkResultRepository = checkResultRepository;
    }

    @Transactional(readOnly = true)
    public RunDiffDto computeDiff(UUID runId) {
        return computeDiff(runId, DiffBaselineStrategy.PREVIOUS_COMPLETED, null);
    }

    @Transactional(readOnly = true)
    public RunDiffDto computeDiff(UUID runId, DiffBaselineStrategy strategy, UUID fixedRunId) {
        RunEntity current = runRepository
                .findById(runId)
                .orElseThrow(() -> new NotFoundException("run not found", "runId"));
        List<CheckResultEntity> currentResults = checkResultsOrderedByKey(runId);
        Map<String, CheckResultEntity> currentByKey = indexByKey(currentResults);

        RunEntity previous = resolveBaseline(current, strategy, fixedRunId);
        if (previous == null) {
            return new RunDiffDto(current.getId().toString(), null, entriesNoPrevious(currentResults));
        }
        List<CheckResultEntity> previousResults = checkResultsOrderedByKey(previous.getId());
        Map<String, CheckResultEntity> previousByKey = indexByKey(previousResults);

        Set<String> keys = new HashSet<>();
        keys.addAll(currentByKey.keySet());
        keys.addAll(previousByKey.keySet());

        List<RunDiffEntry> entries = new ArrayList<>();
        for (String key : keys.stream().sorted().toList()) {
            CheckResultEntity cur = currentByKey.get(key);
            CheckResultEntity prev = previousByKey.get(key);
            entries.add(buildEntry(key, prev, cur));
        }
        entries.sort(Comparator.comparing(RunDiffEntry::checkKey));
        return new RunDiffDto(current.getId().toString(), previous.getId().toString(), entries);
    }

    private RunEntity resolveBaseline(RunEntity current, DiffBaselineStrategy strategy, UUID fixedRunId) {
        DiffBaselineStrategy chosen = strategy == null ? DiffBaselineStrategy.PREVIOUS_COMPLETED : strategy;
        return switch (chosen) {
            case PREVIOUS_COMPLETED -> runRepository.findPreviousCompletedRun(
                            current.getProjectId(),
                            current.getEnvironment(),
                            current.getId(),
                            PageRequest.of(0, 1))
                    .stream()
                    .findFirst()
                    .orElse(null);
            case LAST_PASSED -> runRepository.findPreviousPassedRun(
                            current.getProjectId(),
                            current.getEnvironment(),
                            current.getId(),
                            PageRequest.of(0, 1))
                    .stream()
                    .findFirst()
                    .orElse(null);
            case FIXED_RUN -> resolveFixedBaseline(current, fixedRunId);
        };
    }

    private RunEntity resolveFixedBaseline(RunEntity current, UUID fixedRunId) {
        if (fixedRunId == null) {
            throw new ValidationFailedException("fixedRunId is required for FIXED_RUN baseline", "fixedRunId");
        }
        RunEntity fixed = runRepository.findById(fixedRunId)
                .orElseThrow(() -> new NotFoundException("fixed baseline run not found", "fixedRunId"));
        if (fixed.getId().equals(current.getId())) {
            throw new ValidationFailedException("fixed baseline run must differ from current run", "fixedRunId");
        }
        if (!fixed.getProjectId().equals(current.getProjectId())) {
            throw new ValidationFailedException("fixed baseline run must belong to same project", "fixedRunId");
        }
        if (!Objects.equals(fixed.getEnvironment(), current.getEnvironment())) {
            throw new ValidationFailedException("fixed baseline run must use same environment", "fixedRunId");
        }
        return fixed;
    }

    private List<CheckResultEntity> checkResultsOrderedByKey(UUID runId) {
        return checkResultRepository.findByRunId(runId).stream()
                .sorted(Comparator.comparing(CheckResultEntity::getCheckKey))
                .toList();
    }

    private static Map<String, CheckResultEntity> indexByKey(List<CheckResultEntity> list) {
        Map<String, CheckResultEntity> m = new HashMap<>();
        for (CheckResultEntity e : list) {
            m.put(e.getCheckKey(), e);
        }
        return m;
    }

    private static List<RunDiffEntry> entriesNoPrevious(List<CheckResultEntity> currentResults) {
        List<RunDiffEntry> entries = new ArrayList<>();
        for (CheckResultEntity cur : currentResults) {
            RunDiffChangeType type = switch (cur.getStatus()) {
                case PASSED -> RunDiffChangeType.NEWLY_PASSING;
                case FAILED, ERROR -> RunDiffChangeType.NEWLY_FAILING;
                case SKIPPED -> RunDiffChangeType.STILL_FAILING;
            };
            entries.add(new RunDiffEntry(
                    cur.getCheckKey(),
                    cur.getCheckName(),
                    type,
                    null,
                    cur.getStatus(),
                    null,
                    cur.getActualStatus(),
                    null,
                    cur.getLatencyMs()));
        }
        entries.sort(Comparator.comparing(RunDiffEntry::checkKey));
        return entries;
    }

    private RunDiffEntry buildEntry(String key, CheckResultEntity prev, CheckResultEntity cur) {
        if (prev == null && cur != null) {
            return new RunDiffEntry(
                    key,
                    cur.getCheckName(),
                    RunDiffChangeType.NEWLY_ADDED,
                    null,
                    cur.getStatus(),
                    null,
                    cur.getActualStatus(),
                    null,
                    cur.getLatencyMs());
        }
        if (prev != null && cur == null) {
            return new RunDiffEntry(
                    key,
                    prev.getCheckName(),
                    RunDiffChangeType.REMOVED,
                    prev.getStatus(),
                    null,
                    prev.getActualStatus(),
                    null,
                    prev.getLatencyMs(),
                    null);
        }
        assert prev != null && cur != null;
        CheckResultStatus ps = prev.getStatus();
        CheckResultStatus cs = cur.getStatus();
        if (ps != cs) {
            if (cs == CheckResultStatus.PASSED) {
                return bothKnown(prev, cur, RunDiffChangeType.NEWLY_PASSING);
            }
            if (cs == CheckResultStatus.FAILED || cs == CheckResultStatus.ERROR) {
                return bothKnown(prev, cur, RunDiffChangeType.NEWLY_FAILING);
            }
            return bothKnown(prev, cur, RunDiffChangeType.STILL_FAILING);
        }
        if (ps == CheckResultStatus.PASSED && cs == CheckResultStatus.PASSED) {
            if (!Objects.equals(prev.getActualStatus(), cur.getActualStatus())) {
                return bothKnown(prev, cur, RunDiffChangeType.STATUS_CODE_CHANGED);
            }
            if (latencyChanged(prev.getLatencyMs(), cur.getLatencyMs())) {
                return bothKnown(prev, cur, RunDiffChangeType.LATENCY_CHANGED);
            }
            if (!normalizeAssertions(prev.getAssertionsJson())
                    .equals(normalizeAssertions(cur.getAssertionsJson()))) {
                return bothKnown(prev, cur, RunDiffChangeType.ASSERTION_CHANGED);
            }
            return bothKnown(prev, cur, RunDiffChangeType.STILL_PASSING);
        }
        if (latencyChanged(prev.getLatencyMs(), cur.getLatencyMs())) {
            return bothKnown(prev, cur, RunDiffChangeType.LATENCY_CHANGED);
        }
        if (cs == CheckResultStatus.PASSED) {
            return bothKnown(prev, cur, RunDiffChangeType.STILL_PASSING);
        }
        return bothKnown(prev, cur, RunDiffChangeType.STILL_FAILING);
    }

    private static RunDiffEntry bothKnown(CheckResultEntity prev, CheckResultEntity cur, RunDiffChangeType change) {
        return new RunDiffEntry(
                cur.getCheckKey(),
                cur.getCheckName(),
                change,
                prev.getStatus(),
                cur.getStatus(),
                prev.getActualStatus(),
                cur.getActualStatus(),
                prev.getLatencyMs(),
                cur.getLatencyMs());
    }

    private static String normalizeAssertions(String json) {
        return json == null ? "" : json.trim();
    }

    /**
     * {@code true} when both values are non-null and either absolute delta {@code > 100}ms or relative delta
     * {@code > 50%} of the previous latency (previous {@code > 0}), or previous is 0 and current {@code > 100}ms.
     */
    static boolean latencyChanged(Long previousMs, Long currentMs) {
        if (previousMs == null || currentMs == null) {
            return false;
        }
        long p = previousMs;
        long c = currentMs;
        if (Math.abs(c - p) > 100) {
            return true;
        }
        if (p > 0 && Math.abs(c - p) * 2 > p) {
            return true;
        }
        return p == 0 && c > 100;
    }
}
