package dev.backline.api.service;

import dev.backline.api.exception.NotFoundException;
import dev.backline.api.mapper.ProjectMapper;
import dev.backline.api.mapper.RunMapper;
import dev.backline.api.persistence.repository.ProjectRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.core.api.dto.ProjectSummaryDto;
import dev.backline.core.run.RunStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProjectSummaryService {

    private final ProjectRepository projectRepository;
    private final RunRepository runRepository;

    public ProjectSummaryService(ProjectRepository projectRepository, RunRepository runRepository) {
        this.projectRepository = projectRepository;
        this.runRepository = runRepository;
    }

    @Transactional(readOnly = true)
    public ProjectSummaryDto summarize(UUID projectId) {
        var project = projectRepository
                .findById(projectId)
                .map(ProjectMapper::toDto)
                .orElseThrow(() -> new NotFoundException("project not found", "projectId"));
        long total = runRepository.countByProjectId(projectId);
        long passed = runRepository.countByProjectIdAndStatus(projectId, RunStatus.PASSED);
        long failed = runRepository.countByProjectIdAndStatus(projectId, RunStatus.FAILED);
        long errored = runRepository.countByProjectIdAndStatus(projectId, RunStatus.ERROR);
        var lastRun = runRepository
                .findFirstByProjectIdOrderByQueuedAtDesc(projectId)
                .map(RunMapper::toDto)
                .orElse(null);
        return new ProjectSummaryDto(project, total, passed, failed, errored, lastRun);
    }
}
