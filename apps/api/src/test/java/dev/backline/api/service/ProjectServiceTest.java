package dev.backline.api.service;

import dev.backline.api.exception.ConflictException;
import dev.backline.api.exception.NotFoundException;
import dev.backline.api.exception.ValidationFailedException;
import dev.backline.api.persistence.PostgresTestBase;
import dev.backline.core.api.dto.CreateProjectRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class ProjectServiceTest extends PostgresTestBase {

    @Autowired
    private ProjectService projectService;

    @Test
    void createRejectsDuplicateSlug() {
        String slug = "proj-" + UUID.randomUUID().toString().substring(0, 8);
        projectService.create(new CreateProjectRequest(slug, "One"));

        assertThatThrownBy(() -> projectService.create(new CreateProjectRequest(slug, "Two")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createRejectsInvalidSlug() {
        assertThatThrownBy(() -> projectService.create(new CreateProjectRequest("Bad Slug", "Name")))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void listUsesTrueOffsetPagination() {
        for (int i = 0; i < 3; i++) {
            projectService.create(new CreateProjectRequest("off-" + i + "-" + UUID.randomUUID().toString().substring(0, 4), "P" + i));
        }

        var first = projectService.list(1, 0);
        var second = projectService.list(1, 1);

        assertThat(first.getContent()).hasSize(1);
        assertThat(second.getContent()).hasSize(1);
        assertThat(first.getContent().getFirst().id()).isNotEqualTo(second.getContent().getFirst().id());
    }

    @Test
    void findByIdThrowsForMissingProject() {
        assertThatThrownBy(() -> projectService.findById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }
}
