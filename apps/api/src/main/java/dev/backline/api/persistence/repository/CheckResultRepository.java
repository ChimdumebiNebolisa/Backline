package dev.backline.api.persistence.repository;

import dev.backline.api.persistence.entity.CheckResultEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read/write access to {@link dev.backline.api.persistence.entity.CheckResultEntity} rows. */
public interface CheckResultRepository extends JpaRepository<CheckResultEntity, UUID> {

    List<CheckResultEntity> findByRunId(UUID runId);

    Page<CheckResultEntity> findByCheckId(UUID checkId, Pageable pageable);

    Page<CheckResultEntity> findByCheckIdOrderByCreatedAtDesc(UUID checkId, Pageable pageable);
}
