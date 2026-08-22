package dev.backline.api.persistence.repository;

import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.core.run.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read/write access to {@link dev.backline.api.persistence.entity.RunEntity} rows, including
 * specification-based filtering for list endpoints.
 */
public interface RunRepository extends JpaRepository<RunEntity, UUID>, JpaSpecificationExecutor<RunEntity> {

    /**
     * Cancels a run only while it is still cancellable ({@code QUEUED} or {@code RUNNING}).
     * The conditional update makes cancellation linearizable against worker claim and
     * finalize: whichever transaction touches the row first wins, and a late finalize or
     * late cancel observes zero affected rows instead of overwriting terminal state.
     *
     * @return number of rows cancelled (0 when the run already reached a terminal status)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RunEntity r
            set r.status = dev.backline.core.run.RunStatus.CANCELLED,
                r.finishedAt = :now,
                r.updatedAt = :now,
                r.lockedBy = null,
                r.lockedAt = null,
                r.timeoutAt = null
            where r.id = :runId
              and (r.status = dev.backline.core.run.RunStatus.QUEUED
                   or r.status = dev.backline.core.run.RunStatus.RUNNING)
            """)
    int cancelIfCancellable(@Param("runId") UUID runId, @Param("now") Instant now);

    Optional<RunEntity> findByIdempotencyKey(String key);

    /**
     * Transaction-scoped PostgreSQL advisory lock used to serialize concurrent submissions that
     * carry the same idempotency key. Without it, two simultaneous requests can both observe
     * "no existing run" and race past the check-then-insert path; the unique index would reject
     * one of them as a 500 instead of replaying the winner's row.
     */
    @Query(value = "select pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
    void lockIdempotencyKey(@Param("key") String key);


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
