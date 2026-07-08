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
import static org.assertj.core.api.Assertions.assertThat;
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
                .hasMessageContaining("assertion must set exactly one supported operator");
    }

    @Test
    void syncAcceptsExtendedAssertionOperators() {
        CheckSyncService service = serviceWithWritableRepository();
        CheckSyncRequest request = new CheckSyncRequest(
                "sample",
                "Sample",
                List.of(
                        new CheckDefinitionDto(
                                "health",
                                "Health",
                                HttpMethod.GET,
                                "http://localhost:8081/health",
                                200,
                                null,
                                List.of(
                                        new AssertionDto("$.name", null, null, null, "alice", null, null, null, null, null),
                                        new AssertionDto("$.latency", null, null, null, null, null, 10.0, null, null, null))),
                        new CheckDefinitionDto(
                                "regex",
                                "Regex",
                                HttpMethod.GET,
                                "http://localhost:8081/regex",
                                200,
                                null,
                                List.of(new AssertionDto("$.email", null, null, null, null, ".+@.+", null, null, null, null)))));

        var synced = service.sync(request);
        assertThat(synced).hasSize(2);
    }

    @Test
    void syncRejectsBlankProjectSlug() {
        CheckSyncService service = serviceWithWritableRepository();
        assertThatThrownBy(() -> service.sync(new CheckSyncRequest(" ", "Name", List.of())))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void syncRejectsEmptyChecksList() {
        CheckSyncService service = serviceWithWritableRepository();
        assertThatThrownBy(() -> service.sync(new CheckSyncRequest("sample", "Sample", List.of())))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void syncRejectsDuplicateCheckKeys() {
        CheckSyncService service = serviceWithWritableRepository();
        var check = new CheckDefinitionDto(
                "dup", "Dup", HttpMethod.GET, "http://localhost:8081/health", 200, null, null);
        assertThatThrownBy(() -> service.sync(new CheckSyncRequest("sample", "Sample", List.of(check, check))))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void syncRejectsNonHttpUrl() {
        CheckSyncService service = serviceWithWritableRepository();
        var check = new CheckDefinitionDto(
                "bad", "Bad", HttpMethod.GET, "ftp://example.com", 200, null, null);
        assertThatThrownBy(() -> service.sync(new CheckSyncRequest("sample", "Sample", List.of(check))))
                .isInstanceOf(ValidationFailedException.class);
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
