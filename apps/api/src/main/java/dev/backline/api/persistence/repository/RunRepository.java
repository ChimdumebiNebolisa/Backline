package dev.backline.api.persistence.repository;

import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.core.run.RunStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read/write access to {@link dev.backline.api.persistence.entity.RunEntity} rows, including
 * specification-based filtering for list endpoints.
 */
public interface RunRepository extends JpaRepository<RunEntity, UUID>, JpaSpecificationExecutor<RunEntity> {

    Optional<RunEntity> findByIdempotencyKey(String idempotencyKey);

    Page<RunEntity> findByProjectId(UUID projectId, Pageable pageable);

    @Query(
            """
            select r from RunEntity r
            where r.projectId = :projectId
              and r.environment = :env
              and (r.status = dev.backline.core.run.RunStatus.PASSED or r.status = dev.backline.core.run.RunStatus.FAILED)
              and r.id <> :excludeId
              and r.finishedAt is not null
              and r.queuedAt < :queuedBefore
            order by r.finishedAt desc
            """)
    List<RunEntity> findPreviousCompletedRun(
            @Param("projectId") UUID projectId,
            @Param("env") String env,
            @Param("excludeId") UUID excludeId,
            @Param("queuedBefore") java.time.Instant queuedBefore,
            Pageable pageable);

    @Query(
            """
            select r from RunEntity r
            where r.projectId = :projectId
              and r.environment = :env
              and r.status = dev.backline.core.run.RunStatus.PASSED
              and r.id <> :excludeId
              and r.finishedAt is not null
              and r.queuedAt < :queuedBefore
            order by r.finishedAt desc
            """)
    List<RunEntity> findPreviousPassedRun(
            @Param("projectId") UUID projectId,
            @Param("env") String env,
            @Param("excludeId") UUID excludeId,
            @Param("queuedBefore") java.time.Instant queuedBefore,
            Pageable pageable);

    long countByProjectIdAndStatus(UUID projectId, RunStatus status);

    long countByProjectId(UUID projectId);

    Optional<RunEntity> findFirstByProjectIdOrderByQueuedAtDesc(UUID projectId);
}
