package dev.backline.api.persistence.repository;

import dev.backline.api.persistence.entity.RunEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read/write access to {@link dev.backline.api.persistence.entity.RunEventEntity} rows. */
public interface RunEventRepository extends JpaRepository<RunEventEntity, UUID> {

    List<RunEventEntity> findByRunIdOrderByCreatedAtAsc(UUID runId);
}
