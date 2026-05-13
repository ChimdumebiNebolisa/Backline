package dev.backline.worker.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.check.HttpMethod;
import dev.backline.core.run.RunEventType;
import dev.backline.core.run.RunStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Worker-only JDBC access for claiming runs, persisting results, and finalizing state.
 *
 * <p>Claim uses {@code FOR UPDATE SKIP LOCKED} inside a single transaction so concurrent workers
 * cannot claim the same queued run.
 */
@Repository
public class WorkerRunDao {

    private static final String RETRY_SCHEDULED = "RETRY_SCHEDULED";

    private static final String CLAIM_SELECT = """
            SELECT id, project_id, environment, config_hash, attempt_count
            FROM runs
            WHERE status = 'QUEUED' AND next_attempt_at <= now()
            ORDER BY queued_at ASC
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """;

    private static final String CLAIM_UPDATE = """
            UPDATE runs
            SET status = 'RUNNING',
                locked_by = ?,
                locked_at = now(),
                started_at = coalesce(started_at, now()),
                attempt_count = attempt_count + 1,
                updated_at = now()
            WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public WorkerRunDao(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<ClaimedRun> claimNextRun(String workerId) {
        return transactionTemplate.execute(status -> {
            List<ClaimCandidate> candidates =
                    jdbcTemplate.query(CLAIM_SELECT, (rs, rowNum) -> new ClaimCandidate(
                            rs.getObject("id", UUID.class),
                            rs.getObject("project_id", UUID.class),
                            rs.getString("environment"),
                            rs.getString("config_hash"),
                            rs.getInt("attempt_count")));

            if (candidates.isEmpty()) {
                return Optional.empty();
            }

            ClaimCandidate candidate = candidates.getFirst();
            int updated = jdbcTemplate.update(CLAIM_UPDATE, workerId, candidate.runId());
            if (updated != 1) {
                throw new IllegalStateException("Expected exactly one run row to be claimed");
            }

            insertRunEvent(candidate.runId(), RunEventType.CLAIMED.name(), "Run claimed by worker " + workerId);
            insertRunEvent(candidate.runId(), RunEventType.STARTED.name(), "Run execution started");

            return Optional.of(new ClaimedRun(
                    candidate.runId(),
                    candidate.projectId(),
                    candidate.environment(),
                    candidate.configHash(),
                    candidate.attemptCount() + 1));
        });
    }

    public void writeCheckResult(UUID runId, CheckResultRow row) {
        jdbcTemplate.update(
                """
                        INSERT INTO check_results (
                            id,
                            run_id,
                            check_id,
                            check_key,
                            check_name,
                            status,
                            actual_status,
                            latency_ms,
                            error_code,
                            error_message,
                            response_preview,
                            assertions_json,
                            created_at
                        ) VALUES (
                            gen_random_uuid(),
                            ?,
                            ?,
                            ?,
                            ?,
                            ?,
                            ?,
                            ?,
                            ?,
                            ?,
                            ?,
                            ?::jsonb,
                            now()
                        )
                        """,
                runId,
                row.checkId(),
                row.checkKey(),
                row.checkName(),
                row.status().name(),
                row.actualStatus(),
                row.latencyMs(),
                row.errorCode(),
                row.errorMessage(),
                row.responsePreview(),
                row.assertionsJson() == null ? null : row.assertionsJson());
    }

    public void finalizeRun(UUID runId, RunStatus terminalStatus) {
        finalizeRun(runId, terminalStatus, null);
    }

    /**
     * Moves a run from {@link RunStatus#RUNNING} to a terminal status and appends a lifecycle event.
     *
     * @throws IllegalStateException when no {@code RUNNING} row matches {@code runId}
     */
    public void finalizeRun(UUID runId, RunStatus terminalStatus, String message) {
        int rows = jdbcTemplate.update(
                """
                        UPDATE runs
                        SET status = ?,
                            finished_at = now(),
                            updated_at = now(),
                            locked_by = NULL,
                            locked_at = NULL
                        WHERE id = ?
                          AND status = 'RUNNING'
                        """,
                terminalStatus.name(),
                runId);
        if (rows != 1) {
            throw new IllegalStateException("Expected exactly one RUNNING run to finalize for id " + runId);
        }

        String eventType =
                switch (terminalStatus) {
                    case PASSED -> RunEventType.COMPLETED.name();
                    case FAILED -> RunEventType.FAILED.name();
                    case ERROR -> RunEventType.ERRORED.name();
                    default -> throw new IllegalArgumentException("Unsupported terminal status: " + terminalStatus);
                };

        String resolvedMessage = message;
        if (resolvedMessage == null) {
            resolvedMessage =
                    switch (terminalStatus) {
                        case PASSED -> "Run completed successfully";
                        case FAILED -> "Run completed with failing checks";
                        case ERROR -> "Run completed with error";
                        default -> terminalStatus.name();
                    };
        }

        insertRunEvent(runId, eventType, resolvedMessage);
    }

    /**
     * Clears partial results, returns the run to {@link RunStatus#QUEUED}, and schedules the next attempt.
     */
    public void requeueForRetry(UUID runId, long backoffMs) {
        jdbcTemplate.update("DELETE FROM check_results WHERE run_id = ?", runId);
        int rows = jdbcTemplate.update(
                """
                        UPDATE runs
                        SET status = 'QUEUED',
                            next_attempt_at = ?,
                            locked_by = NULL,
                            locked_at = NULL,
                            updated_at = now()
                        WHERE id = ?
                          AND status = 'RUNNING'
                        """,
                Timestamp.from(Instant.now().plusMillis(backoffMs)),
                runId);
        if (rows != 1) {
            throw new IllegalStateException("Expected exactly one RUNNING run to requeue for id " + runId);
        }

        insertRunEvent(runId, RETRY_SCHEDULED, "Scheduled retry after " + backoffMs + " ms backoff");
    }

    public List<CheckRow> loadChecksForProject(UUID projectId) {
        return jdbcTemplate.query(
                """
                        SELECT id, key, name, method, url, expected_status, max_latency_ms, assertions_json, config_hash
                        FROM checks
                        WHERE project_id = ?
                          AND active = TRUE
                        ORDER BY key
                        """,
                (rs, rowNum) -> {
                    String assertionsText = rs.getString("assertions_json");
                    List<AssertionDto> assertions = parseAssertions(assertionsText);
                    return new CheckRow(
                            rs.getObject("id", UUID.class),
                            rs.getString("key"),
                            rs.getString("name"),
                            HttpMethod.valueOf(rs.getString("method")),
                            rs.getString("url"),
                            rs.getInt("expected_status"),
                            rs.getObject("max_latency_ms") == null ? null : rs.getInt("max_latency_ms"),
                            assertions,
                            rs.getString("config_hash"));
                },
                projectId);
    }

    private List<AssertionDto> parseAssertions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse assertions_json", ex);
        }
    }

    private void insertRunEvent(UUID runId, String eventType, String message) {
        jdbcTemplate.update(
                """
                        INSERT INTO run_events (id, run_id, event_type, message, created_at)
                        VALUES (gen_random_uuid(), ?, ?, ?, now())
                        """,
                runId,
                eventType,
                message);
    }

    private record ClaimCandidate(UUID runId, UUID projectId, String environment, String configHash, int attemptCount) {}
}
