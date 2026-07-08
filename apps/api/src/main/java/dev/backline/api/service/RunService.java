package dev.backline.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import dev.backline.core.run.RunEventType;
import dev.backline.core.run.RunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     */
    @Transactional
    public RunDto submit(CreateRunRequest req) {
        validateSubmit(req);
        ProjectEntity project = projectService.requireBySlug(req.projectSlug().trim());
        if (req.idempotencyKey() != null && !req.idempotencyKey().isBlank()) {
            return runRepository
                    .findByIdempotencyKey(req.idempotencyKey().trim())
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
        if (req.configHash() == null || req.configHash().isBlank()) {
            throw new dev.backline.api.exception.ValidationFailedException("configHash is required", "configHash");
        }
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
