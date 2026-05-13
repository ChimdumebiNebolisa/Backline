package dev.backline.api.mapper;

import dev.backline.api.persistence.entity.RunEventEntity;
import dev.backline.core.api.dto.RunEventDto;
import dev.backline.core.run.RunEventType;

public final class RunEventMapper {

    private RunEventMapper() {}

    public static RunEventDto toDto(RunEventEntity e) {
        return new RunEventDto(
                e.getId().toString(),
                e.getRunId().toString(),
                RunEventType.valueOf(e.getEventType()),
                e.getMessage(),
                e.getCreatedAt());
    }
}
