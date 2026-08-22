package dev.backline.worker.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.check.HttpMethod;
import dev.backline.core.contract.ContractSettingsDto;
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
                timeout_at = ?,
                updated_at = now()
            WHERE id = ?
            """;

    // Ownership fence: locked_by must still match the claiming worker so a stale
    // worker cannot finalize or requeue a run that was recovered and re-claimed
    // by another worker while it was executing.
    private static final String FINALIZE_UPDATE = """
            UPDATE runs
            SET status = ?,
                finished_at = now(),
                updated_at = now(),
                locked_by = NULL,
                locked_at = NULL,
                timeout_at = NULL,
                last_error = CASE WHEN ? IN ('ERROR') THEN ? ELSE last_error END
            WHERE id = ?
              AND status = 'RUNNING'
              AND locked_by = ?
            """;

    private static final String REQUEUE_UPDATE = """
            UPDATE runs
            SET status = 'QUEUED',
                next_attempt_at = ?,
                locked_by = NULL,
                locked_at = NULL,
                updated_at = now()
            WHERE id = ?
              AND status = 'RUNNING'
              AND locked_by = ?
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
        return claimNextRun(workerId, 0L);
    }

    public Optional<ClaimedRun> claimNextRun(String workerId, long jobTimeoutMs) {
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
            Timestamp timeoutAt = jobTimeoutMs > 0
                    ? Timestamp.from(Instant.now().plusMillis(jobTimeoutMs))
                    : null;
            int updated = jdbcTemplate.update(CLAIM_UPDATE, workerId, timeoutAt, candidate.runId());
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
                    candidate.attemptCount() + 1,
                    workerId));
        });
    }

    public void writeCheckResult(UUID runId, CheckResultRow row) {
        writeCheckResultInternal(runId, row);
    }

    /**
     * Persists all check results and moves the run to its terminal status in one transaction.
     * The terminal update is fenced on the claim owner ({@code locked_by}); when ownership was
     * lost because another worker recovered and re-claimed the run, nothing is written and
     * {@code false} is returned so a stale attempt cannot overwrite the new owner's execution.
     */
    public boolean persistResultsAndFinalize(
            UUID runId, List<CheckResultRow> rows, RunStatus terminalStatus, String ownerWorkerId) {
        Boolean finalized = transactionTemplate.execute(status -> {
            int claimed = jdbcTemplate.update(FINALIZE_UPDATE,
                    terminalStatus.name(),
                    terminalStatus.name(),
                    (String) null,
                    runId,
                    ownerWorkerId);
            if (claimed != 1) {
                status.setRollbackOnly();
                return Boolean.FALSE;
            }
            for (CheckResultRow row : rows) {
                writeCheckResultInternal(runId, row);
            }
            appendTerminalRunEvent(runId, terminalStatus, null);
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(finalized);
    }

    private void writeCheckResultInternal(UUID runId, CheckResultRow row) {
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
                            response_contract_json,
                            response_contract_hash,
                            response_contract_status,
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
                            ?::jsonb,
                            ?,
                            ?,
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
                row.assertionsJson() == null ? null : row.assertionsJson(),
                row.responseContractJson() == null ? null : row.responseContractJson(),
                row.responseContractHash(),
                row.responseContractStatus() == null ? null : row.responseContractStatus().name());
    }

    public boolean finalizeRun(UUID runId, RunStatus terminalStatus, String ownerWorkerId) {
        return finalizeRun(runId, terminalStatus, null, ownerWorkerId);
    }

    /**
     * Moves a run from {@code RUNNING} to a terminal status and appends a lifecycle event.
     * Returns {@code true} when the run was finalized; {@code false} when this worker no longer
     * owns the claim (the run was recovered or re-claimed elsewhere) and must not be touched.
     */
    public boolean finalizeRun(UUID runId, RunStatus terminalStatus, String message, String ownerWorkerId) {
        Boolean finalized = transactionTemplate.execute(status -> {
            int rows = jdbcTemplate.update(FINALIZE_UPDATE,
                    terminalStatus.name(),
                    terminalStatus.name(),
                    message,
                    runId,
                    ownerWorkerId);
            if (rows != 1) {
                return Boolean.FALSE;
            }
            appendTerminalRunEvent(runId, terminalStatus, message);
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(finalized);
    }

    private void appendTerminalRunEvent(UUID runId, RunStatus terminalStatus, String message) {
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
     * Clears partial results, returns the run to {@link RunStatus#QUEUED}, and schedules the next
     * attempt. Fenced like {@link #finalizeRun}: returns {@code false} without changing state when
     * another worker owns the claim.
     */
    public boolean requeueForRetry(UUID runId, long backoffMs, String ownerWorkerId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            int rows = jdbcTemplate.update(REQUEUE_UPDATE,
                    Timestamp.from(Instant.now().plusMillis(backoffMs)),
                    runId,
                    ownerWorkerId);
            if (rows != 1) {
                return Boolean.FALSE;
            }
            jdbcTemplate.update("DELETE FROM check_results WHERE run_id = ?", runId);
            insertRunEvent(runId, RETRY_SCHEDULED, "Scheduled retry after " + backoffMs + " ms backoff");
            return Boolean.TRUE;
        }));
    }

    /**
     * Recovers RUNNING runs whose worker appears stale (locked_at older than threshold)
     * or whose timeout_at has passed. Each recovered run is either requeued for retry
     * (if attempts remain) or marked ERROR. Uses FOR UPDATE SKIP LOCKED for safety
     * with multiple workers.
     *
     * @return number of runs recovered
     */
    public int recoverStaleRuns(long staleThresholdMs, int maxAttempts, long retryBackoffMs) {
        return transactionTemplate.execute(status -> {
            Timestamp staleDeadline = Timestamp.from(Instant.now().minusMillis(staleThresholdMs));
            Timestamp now = Timestamp.from(Instant.now());
            List<StaleCandidate> stale = jdbcTemplate.query(
                    """
                    SELECT id, attempt_count
                    FROM runs
                    WHERE status = 'RUNNING'
                      AND (locked_at <= ? OR (timeout_at IS NOT NULL AND timeout_at <= ?))
                    FOR UPDATE SKIP LOCKED
                    """,
                    (rs, rowNum) -> new StaleCandidate(
                            rs.getObject("id", UUID.class),
                            rs.getInt("attempt_count")),
                    staleDeadline,
                    now);

            for (StaleCandidate candidate : stale) {
                jdbcTemplate.update("DELETE FROM check_results WHERE run_id = ?", candidate.runId());
                if (candidate.attemptCount() < maxAttempts) {
                    jdbcTemplate.update(
                            """
                            UPDATE runs
                            SET status = 'QUEUED',
                                next_attempt_at = ?,
                                locked_by = NULL,
                                locked_at = NULL,
                                timeout_at = NULL,
                                last_error = 'Recovered from stale RUNNING state',
                                updated_at = now()
                            WHERE id = ?
                            """,
                            Timestamp.from(Instant.now().plusMillis(retryBackoffMs)),
                            candidate.runId());
                    insertRunEvent(candidate.runId(), RunEventType.STALE_RECOVERED.name(),
                            "Stale RUNNING run requeued for retry (attempt " + candidate.attemptCount() + "/" + maxAttempts + ")");
                } else {
                    jdbcTemplate.update(
                            """
                            UPDATE runs
                            SET status = 'ERROR',
                                finished_at = now(),
                                locked_by = NULL,
                                locked_at = NULL,
                                timeout_at = NULL,
                                last_error = 'Stale RUNNING after max attempts exhausted',
                                updated_at = now()
                            WHERE id = ?
                            """,
                            candidate.runId());
                    insertRunEvent(candidate.runId(), RunEventType.STALE_RECOVERED.name(),
                            "Stale RUNNING run marked ERROR — max attempts exhausted (" + candidate.attemptCount() + "/" + maxAttempts + ")");
                }
            }
            return stale.size();
        });
    }

    /**
     * Checks whether a run has been cancelled while the worker is executing it.
     */
    public boolean isRunCancelled(UUID runId) {
        String st = jdbcTemplate.queryForObject("SELECT status FROM runs WHERE id = ?", String.class, runId);
        return "CANCELLED".equals(st);
    }

    private record StaleCandidate(UUID runId, int attemptCount) {}

    public List<CheckRow> loadChecksForProject(UUID projectId) {
        return jdbcTemplate.query(
                """
                        SELECT id, key, name, method, url, expected_status, max_latency_ms, assertions_json, config_hash, contract_json
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
                            rs.getString("config_hash"),
                            parseContract(rs.getString("contract_json")));
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

    private ContractSettingsDto parseContract(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ContractSettingsDto.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse contract_json", ex);
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
