package dev.backline.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.exception.ConflictException;
import dev.backline.api.exception.NotFoundException;
import dev.backline.api.mapper.CheckResultMapper;
import dev.backline.api.mapper.RunEventMapper;
import dev.backline.api.mapper.RunMapper;
import dev.backline.api.persistence.entity.CheckResultEntity;
import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.entity.RunEventEntity;
import dev.backline.api.persistence.repository.CheckResultRepository;
import dev.backline.api.persistence.repository.RunEventRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.core.api.dto.CheckResultDto;
import dev.backline.core.api.dto.CreateRunRequest;
import dev.backline.core.api.dto.RunDto;
import dev.backline.core.api.dto.RunEventDto;
import dev.backline.core.error.ErrorCode;
import dev.backline.core.run.RunEventType;
import dev.backline.core.run.RunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;


@Service
public class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);

    private final RunRepository runRepository;
    private final RunEventRepository runEventRepository;
    private final CheckResultRepository checkResultRepository;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    public RunService(
            RunRepository runRepository,
            RunEventRepository runEventRepository,
            CheckResultRepository checkResultRepository,
            ProjectService projectService,
            ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.runEventRepository = runEventRepository;
        this.checkResultRepository = checkResultRepository;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a queued run. Requires the project to exist (via {@code POST /api/projects} or prior sync that
     * created it) so clients follow an explicit project lifecycle instead of implicitly creating projects on run
     * submission.
     *
     * <p>When an idempotency key is present, a transaction-scoped advisory lock serializes concurrent
     * duplicates so the loser observes and replays the winner's run instead of failing on the unique
     * index (docs/contracts.md: duplicate key returns the same run row).
     */
    @Transactional
    public RunDto submit(CreateRunRequest req) {
        validateSubmit(req);
        ProjectEntity project = projectService.requireBySlug(req.projectSlug().trim());
        if (req.idempotencyKey() != null && !req.idempotencyKey().isBlank()) {
            String key = req.idempotencyKey().trim();
            runRepository.lockIdempotencyKey(key);
            return runRepository
                    .findByIdempotencyKey(key)
                    .map(RunMapper::toDto)
                    .orElseGet(() -> createQueuedRun(project, req));
        }
        return createQueuedRun(project, req);
    }

    private RunDto createQueuedRun(ProjectEntity project, CreateRunRequest req) {
        RunEntity run = new RunEntity();
        run.setProjectId(project.getId());
        run.setEnvironment(req.environment().trim());
        run.setStatus(RunStatus.QUEUED);
        run.setConfigHash(req.configHash());
        run.setSource(req.source() != null && !req.source().isBlank() ? req.source().trim() : null);
        run.setIdempotencyKey(
                req.idempotencyKey() != null && !req.idempotencyKey().isBlank()
                        ? req.idempotencyKey().trim()
                        : null);
        run.setAttemptCount(0);
        run = runRepository.save(run);

        RunEventEntity event = new RunEventEntity();
        event.setRunId(run.getId());
        event.setEventType(RunEventType.SUBMITTED.name());
        event.setMessage("Run queued");
        runEventRepository.save(event);

        log.info("submitted run id={} projectSlug={}", run.getId(), project.getSlug());
        return RunMapper.toDto(run);
    }

    private static void validateSubmit(CreateRunRequest req) {
        if (req.projectSlug() == null || req.projectSlug().isBlank()) {
            throw new dev.backline.api.exception.ValidationFailedException("projectSlug is required", "projectSlug");
        }
        if (req.environment() == null || req.environment().isBlank()) {
            throw new dev.backline.api.exception.ValidationFailedException("environment is required", "environment");
        }
        if (req.environment().length() > 60) {
            throw new dev.backline.api.exception.ValidationFailedException(
                    "environment must be at most 60 characters", "environment");
        }
        if (req.configHash() == null || req.configHash().isBlank()) {
            throw new dev.backline.api.exception.ValidationFailedException("configHash is required", "configHash");
        }
        if (req.configHash().length() > 128) {
            throw new dev.backline.api.exception.ValidationFailedException(
                    "configHash must be at most 128 characters", "configHash");
        }
        if (req.source() != null && req.source().length() > 60) {
            throw new dev.backline.api.exception.ValidationFailedException(
                    "source must be at most 60 characters", "source");
        }
        if (req.idempotencyKey() != null && req.idempotencyKey().length() > 180) {
            throw new dev.backline.api.exception.ValidationFailedException(
                    "idempotencyKey must be at most 180 characters", "idempotencyKey");
        }
    }

    /**
     * Cancels a queued or running run. Cancellation is linearizable against worker claim and
     * finalize: the conditional update only succeeds while the row is non-terminal, so a cancel
     * racing a completing worker either wins (worker observes {@code CANCELLED} through
     * {@code isRunCancelled} and skips finalization) or loses cleanly (409). Terminal runs are
     * immutable and can never be moved back.
     */
    @Transactional
    public RunDto cancel(UUID runId) {
        RunEntity run = runRepository
                .findById(runId)
                .orElseThrow(() -> new NotFoundException("run not found", "runId"));
        if (run.getStatus().isTerminal()) {
            throw new ConflictException(ErrorCode.CONFLICT, "run already finished with status " + run.getStatus(), "runId");
        }
        Instant now = Instant.now();
        int cancelled = runRepository.cancelIfCancellable(runId, now);
        if (cancelled != 1) {
            // Reached a terminal state between the read above and this update.
            throw new ConflictException(ErrorCode.CONFLICT, "run already reached a terminal state", "runId");
        }

        RunEventEntity event = new RunEventEntity();
        event.setRunId(runId);
        event.setEventType(RunEventType.CANCELLED.name());
        event.setMessage("Run cancelled by client while " + run.getStatus());
        runEventRepository.save(event);

        log.info("cancelled run id={} previousStatus={}", runId, run.getStatus());

        RunEntity reloaded = runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("run not found", "runId"));
        return RunMapper.toDto(reloaded);
    }

    @Transactional(readOnly = true)
    public RunDto findById(UUID id) {
        return runRepository
                .findById(id)
                .map(RunMapper::toDto)
                .orElseThrow(() -> new NotFoundException("run not found", "runId"));
    }

    @Transactional(readOnly = true)
    public Page<RunDto> list(RunFilter filter, int limit, int offset) {
        var sort = Sort.by(Sort.Direction.DESC, "queuedAt");
        var pageable = new OffsetBasedPageRequest(limit, offset, sort);
        return runRepository.findAll(RunSpecifications.forFilter(filter), pageable).map(RunMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<CheckResultDto> resultsForRun(UUID runId) {
        requireRun(runId);
        return checkResultRepository.findByRunId(runId).stream()
                .sorted(Comparator.comparing(CheckResultEntity::getCheckKey))
                .map(e -> CheckResultMapper.toDto(e, objectMapper))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RunEventDto> eventsForRun(UUID runId) {
        requireRun(runId);
        return runEventRepository.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .map(RunEventMapper::toDto)
                .toList();
    }

    private void requireRun(UUID runId) {
        if (!runRepository.existsById(runId)) {
            throw new NotFoundException("run not found", "runId");
        }
    }
}
