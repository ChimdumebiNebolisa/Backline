package dev.backline.api.service;

import dev.backline.api.exception.ConflictException;
import dev.backline.api.exception.NotFoundException;
import dev.backline.api.mapper.ProjectMapper;
import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.repository.ProjectRepository;
import dev.backline.core.api.dto.CreateProjectRequest;
import dev.backline.core.api.dto.ProjectDto;
import dev.backline.core.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProjectService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9-]{1,120}$");

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public ProjectDto create(CreateProjectRequest req) {
        String slug = req.slug().trim();
        String name = req.name().trim();
        validateSlug(slug);
        validateName(name);
        if (projectRepository.existsBySlug(slug)) {
            throw new ConflictException(ErrorCode.CONFLICT, "project slug already exists", "slug");
        }
        ProjectEntity e = new ProjectEntity();
        e.setSlug(slug);
        e.setName(name);
        return ProjectMapper.toDto(projectRepository.save(e));
    }

    @Transactional(readOnly = true)
    public Page<ProjectDto> list(int limit, int offset) {
        var sort = Sort.by(Sort.Direction.DESC, "createdAt");
        // PageRequest is index-based; use page = offset/limit (see ARCHITECTURE list endpoints).
        var pageable = PageRequest.of(limit > 0 ? offset / limit : 0, limit, sort);
        return projectRepository.findAll(pageable).map(ProjectMapper::toDto);
    }

    @Transactional(readOnly = true)
    public ProjectDto findById(UUID id) {
        return projectRepository
                .findById(id)
                .map(ProjectMapper::toDto)
                .orElseThrow(() -> new NotFoundException("project not found", "projectId"));
    }

    @Transactional(readOnly = true)
    public ProjectEntity requireBySlug(String slug) {
        String s = slug == null ? "" : slug.trim();
        return projectRepository
                .findBySlug(s)
                .orElseThrow(() -> new NotFoundException("project not found for slug", "projectSlug"));
    }

    /**
     * Used by check sync to ensure a project row exists before persisting checks. Slug is normalized to lowercase
     * externally by validation rules.
     */
    @Transactional
    public ProjectEntity getOrCreateBySlug(String slug, String name) {
        String s = slug.trim();
        validateSlug(s);
        return projectRepository
                .findBySlug(s)
                .orElseGet(() -> {
                    ProjectEntity p = new ProjectEntity();
                    p.setSlug(s);
                    String n = (name == null || name.isBlank()) ? s : name.trim();
                    validateName(n);
                    p.setName(n);
                    return projectRepository.save(p);
                });
    }

    private static void validateSlug(String slug) {
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new dev.backline.api.exception.ValidationFailedException(
                    "slug must be 1-120 lowercase letters, digits, or dashes", "slug");
        }
    }

    private static void validateName(String name) {
        if (name.isBlank() || name.length() > 200) {
            throw new dev.backline.api.exception.ValidationFailedException(
                    "name must be non-blank and at most 200 characters", "name");
        }
    }
}
