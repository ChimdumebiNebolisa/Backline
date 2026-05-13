package dev.backline.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.repository.ProjectRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProjectRepositoryTest extends PostgresTestBase {

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void saveFindBySlugAndExistsBySlugRoundTrip() {
        String slug = "proj-" + UUID.randomUUID();

        ProjectEntity p = new ProjectEntity();
        p.setSlug(slug);
        p.setName("My Project");
        projectRepository.saveAndFlush(p);

        assertThat(projectRepository.findBySlug(slug)).isPresent().get().satisfies(found -> {
            assertThat(found.getId()).isNotNull();
            assertThat(found.getSlug()).isEqualTo(slug);
            assertThat(found.getName()).isEqualTo("My Project");
        });

        assertThat(projectRepository.existsBySlug(slug)).isTrue();
        assertThat(projectRepository.existsBySlug("missing-" + slug)).isFalse();
    }
}
