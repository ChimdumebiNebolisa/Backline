package dev.backline.api.mapper;

import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.core.api.dto.RunDto;

public final class RunMapper {

    private RunMapper() {}

    public static RunDto toDto(RunEntity e) {
        return new RunDto(
                e.getId().toString(),
                e.getProjectId().toString(),
                e.getEnvironment(),
                e.getStatus(),
                e.getConfigHash(),
                e.getSource(),
                e.getIdempotencyKey(),
                e.getQueuedAt(),
                e.getStartedAt(),
                e.getFinishedAt(),
                e.getAttemptCount());
    }
}
