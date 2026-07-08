package dev.backline.api.persistence.repository;

import dev.backline.api.persistence.entity.CheckResultEntity;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.run.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read/write access to {@link dev.backline.api.persistence.entity.CheckResultEntity} rows. */
public interface CheckResultRepository extends JpaRepository<CheckResultEntity, UUID> {

    List<CheckResultEntity> findByRunId(UUID runId);

    Page<CheckResultEntity> findByCheckId(UUID checkId, Pageable pageable);

    Page<CheckResultEntity> findByCheckIdOrderByCreatedAtDesc(UUID checkId, Pageable pageable);

    @Query(
            value = """
            select cr.run_id as runId,
                   r.status as runStatus,
                   cr.status as resultStatus,
                   cr.actual_status as actualStatus,
                   cr.latency_ms as latencyMs,
                   cr.created_at as createdAt
            from check_results cr
            join runs r on r.id = cr.run_id
            where cr.check_id = :checkId
            order by cr.created_at desc
            """,
            countQuery = """
            select count(*)
            from check_results cr
            where cr.check_id = :checkId
            """,
            nativeQuery = true)
    Page<CheckHistoryProjection> findHistoryByCheckId(@Param("checkId") UUID checkId, Pageable pageable);

    interface CheckHistoryProjection {
        UUID getRunId();
        RunStatus getRunStatus();
        CheckResultStatus getResultStatus();
        Integer getActualStatus();
        Long getLatencyMs();
        Instant getCreatedAt();
    }
}
