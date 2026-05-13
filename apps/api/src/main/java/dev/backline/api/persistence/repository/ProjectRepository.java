package dev.backline.api.persistence.repository;

import dev.backline.api.persistence.entity.ProjectEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read/write access to {@link dev.backline.api.persistence.entity.ProjectEntity} rows. */
public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    Optional<ProjectEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
