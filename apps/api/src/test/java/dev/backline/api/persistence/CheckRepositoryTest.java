package dev.backline.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.backline.api.persistence.entity.CheckEntity;
import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.repository.CheckRepository;
import dev.backline.api.persistence.repository.ProjectRepository;
import dev.backline.core.check.HttpMethod;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CheckRepositoryTest extends PostgresTestBase {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private CheckRepository checkRepository;

    @Test
    void findByProjectIdAndKeyReturnsMatchingRow() {
        ProjectEntity project = persistProject();
        CheckEntity check = newCheck(project.getId(), "health", true);
        checkRepository.saveAndFlush(check);

        assertThat(checkRepository.findByProjectIdAndKey(project.getId(), "health"))
                .isPresent()
                .get()
                .extracting(CheckEntity::getName, CheckEntity::isActive)
                .containsExactly("Health", true);
    }

    @Test
    void findByProjectIdAndActiveTrueFiltersInactive() {
        ProjectEntity project = persistProject();
        checkRepository.saveAndFlush(newCheck(project.getId(), "a", true));
        checkRepository.saveAndFlush(newCheck(project.getId(), "b", false));

        assertThat(checkRepository.findByProjectIdAndActiveTrue(project.getId()))
                .extracting(CheckEntity::getKey)
                .containsExactly("a");

        assertThat(checkRepository.findByProjectId(project.getId()))
                .extracting(CheckEntity::getKey)
                .containsExactlyInAnyOrder("a", "b");
    }

    private ProjectEntity persistProject() {
        ProjectEntity p = new ProjectEntity();
        p.setSlug("slug-" + UUID.randomUUID());
        p.setName("P");
        return projectRepository.saveAndFlush(p);
    }

    private static CheckEntity newCheck(UUID projectId, String key, boolean active) {
        CheckEntity c = new CheckEntity();
        c.setProjectId(projectId);
        c.setKey(key);
        c.setName(key.equals("health") ? "Health" : key.toUpperCase());
        c.setMethod(HttpMethod.GET);
        c.setUrl("http://localhost/" + key);
        c.setExpectedStatus(200);
        c.setConfigHash("h");
        c.setActive(active);
        return c;
    }
}
