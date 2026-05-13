package dev.backline.api.mapper;

import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.core.api.dto.ProjectDto;

public final class ProjectMapper {

    private ProjectMapper() {}

    public static ProjectDto toDto(ProjectEntity e) {
        return new ProjectDto(
                e.getId().toString(), e.getSlug(), e.getName(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
