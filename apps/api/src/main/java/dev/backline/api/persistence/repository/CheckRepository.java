package dev.backline.api.persistence.repository;

import dev.backline.api.persistence.entity.CheckEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read/write access to {@link dev.backline.api.persistence.entity.CheckEntity} rows. */
public interface CheckRepository extends JpaRepository<CheckEntity, UUID> {

    Optional<CheckEntity> findByProjectIdAndKey(UUID projectId, String key);

    List<CheckEntity> findByProjectIdAndActiveTrue(UUID projectId);

    List<CheckEntity> findByProjectId(UUID projectId);
}
