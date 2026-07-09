package dev.backline.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.exception.ValidationFailedException;
import dev.backline.api.persistence.entity.CheckEntity;
import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.repository.CheckRepository;
import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.api.dto.CheckDefinitionDto;
import dev.backline.core.api.dto.CheckSyncRequest;
import dev.backline.core.check.HttpMethod;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckSyncServiceTest {

    @Test
    void syncRejectsAssertionWithoutOperation() {
        CheckSyncService service = serviceWithWritableRepository();
        CheckSyncRequest request = new CheckSyncRequest(
                "sample",
                "Sample",
                List.of(new CheckDefinitionDto(
                        "health",
                        "Health",
                        HttpMethod.GET,
                        "http://localhost:8081/health",
                        200,
                        null,
                        List.of(new AssertionDto("$.id", null, null)))));

        assertThatThrownBy(() -> service.sync(request))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("assertion must set at least one of equals or exists");
    }

    @Test
    void syncRejectsMalformedCheckKey() {
        CheckSyncService service = serviceWithWritableRepository();
        CheckSyncRequest request = new CheckSyncRequest(
                "sample",
                "Sample",
                List.of(new CheckDefinitionDto(
                        "Bad Key",
                        "Health",
                        HttpMethod.GET,
                        "http://localhost:8081/health",
                        200,
                        null,
                        null)));

        assertThatThrownBy(() -> service.sync(request))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("check key must match [a-z0-9][a-z0-9-]{0,119}");
    }

    private static CheckSyncService serviceWithWritableRepository() {
        CheckRepository checkRepository = mock(CheckRepository.class);
        ProjectService projectService = mock(ProjectService.class);
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setSlug("sample");
        project.setName("Sample");
        when(projectService.getOrCreateBySlug(anyString(), any())).thenReturn(project);
        when(checkRepository.findByProjectId(any())).thenReturn(List.of());
        when(checkRepository.findByProjectIdAndKey(any(), anyString())).thenReturn(Optional.empty());
        when(checkRepository.save(any(CheckEntity.class))).thenAnswer(invocation -> {
            CheckEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            return entity;
        });
        return new CheckSyncService(checkRepository, projectService, new ObjectMapper());
    }
}
