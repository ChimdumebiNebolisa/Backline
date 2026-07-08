package dev.backline.worker;

import dev.backline.core.run.RunStatus;
import dev.backline.worker.persistence.WorkerRunDao;
import dev.backline.worker.support.PostgresWorkerTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for stale RUNNING recovery, retry exhaustion, cancellation semantics,
 * and timeout behavior.
 */
class WorkerRecoveryTest extends PostgresWorkerTestBase {

    @Autowired
    private WorkerRunDao dao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void staleRunningRunIsRequeuedForRetry() {
        UUID projectId = insertProject();
        UUID runId = insertRunInState(projectId, "RUNNING", 1, minutesAgo(10), "worker-dead");
        insertPartialResult(runId);

        int recovered = dao.recoverStaleRuns(300_000, 3, 1000);
        assertThat(recovered).isEqualTo(1);

        String status = getRunStatus(runId);
        assertThat(status).isEqualTo("QUEUED");

        String lastError = jdbcTemplate.queryForObject(
                "SELECT last_error FROM runs WHERE id = ?", String.class, runId);
        assertThat(lastError).contains("Recovered from stale");

        Long events = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM run_events WHERE run_id = ? AND event_type = 'STALE_RECOVERED'",
                Long.class, runId);
        assertThat(events).isEqualTo(1L);
        Integer remainingResults =
                jdbcTemplate.queryForObject("SELECT count(*) FROM check_results WHERE run_id = ?", Integer.class, runId);
        assertThat(remainingResults).isZero();
    }

    @Test
    void staleRunningRunMarkedErrorAfterMaxAttempts() {
        UUID projectId = insertProject();
        UUID runId = insertRunInState(projectId, "RUNNING", 3, minutesAgo(10), "worker-dead");

        int recovered = dao.recoverStaleRuns(300_000, 3, 1000);
        assertThat(recovered).isEqualTo(1);

        String status = getRunStatus(runId);
        assertThat(status).isEqualTo("ERROR");

        String lastError = jdbcTemplate.queryForObject(
                "SELECT last_error FROM runs WHERE id = ?", String.class, runId);
        assertThat(lastError).contains("max attempts exhausted");
    }

    @Test
    void recentlyClaimedRunNotRecoveredAsStale() {
        UUID projectId = insertProject();
        UUID runId = insertRunInState(projectId, "RUNNING", 1, Instant.now(), "worker-alive");

        int recovered = dao.recoverStaleRuns(300_000, 3, 1000);
        assertThat(recovered).isZero();

        assertThat(getRunStatus(runId)).isEqualTo("RUNNING");
    }

    @Test
    void timedOutRunIsRecoveredEvenIfLockedAtIsRecent() {
        UUID projectId = insertProject();
        UUID runId = insertRunWithTimeout(projectId, "RUNNING", 1, Instant.now(), minutesAgo(1), "worker-slow");

        int recovered = dao.recoverStaleRuns(300_000, 3, 1000);
        assertThat(recovered).isEqualTo(1);

        assertThat(getRunStatus(runId)).isEqualTo("QUEUED");
    }

    @Test
    void staleRecoveryDoesNotDoubleProcessWithMultipleWorkers() {
        UUID projectId = insertProject();
        for (int i = 0; i < 10; i++) {
            insertRunInState(projectId, "RUNNING", 1, minutesAgo(10), "worker-dead-" + i);
        }

        int recovered1 = dao.recoverStaleRuns(300_000, 3, 1000);
        int recovered2 = dao.recoverStaleRuns(300_000, 3, 1000);
        assertThat(recovered1).isEqualTo(10);
        assertThat(recovered2).isZero();
    }

    @Test
    void cancelledRunIsNotClaimedByWorker() {
        UUID projectId = insertProject();
        UUID runId = insertRunInState(projectId, "CANCELLED", 0, null, null);

        var claimed = dao.claimNextRun("worker-a");
        assertThat(claimed).isEmpty();

        assertThat(getRunStatus(runId)).isEqualTo("CANCELLED");
    }

    @Test
    void isRunCancelledReturnsTrueForCancelledRun() {
        UUID projectId = insertProject();
        UUID runId = insertRunInState(projectId, "CANCELLED", 0, null, null);

        assertThat(dao.isRunCancelled(runId)).isTrue();
    }

    @Test
    void isRunCancelledReturnsFalseForRunningRun() {
        UUID projectId = insertProject();
        UUID runId = insertRunInState(projectId, "RUNNING", 1, Instant.now(), "worker-a");

        assertThat(dao.isRunCancelled(runId)).isFalse();
    }

    @Test
    void retryCountIncrementsCorrectlyAcrossAttempts() {
        UUID projectId = insertProject();
        insertCheck(projectId, "test-check");
        UUID runId = insertQueuedRun(projectId);

        var first = dao.claimNextRun("worker-a");
        assertThat(first).isPresent();
        assertThat(first.get().attemptCount()).isEqualTo(1);

        dao.requeueForRetry(runId, 0);

        var second = dao.claimNextRun("worker-a");
        assertThat(second).isPresent();
        assertThat(second.get().attemptCount()).isEqualTo(2);
        assertThat(second.get().runId()).isEqualTo(runId);

        dao.finalizeRun(runId, RunStatus.ERROR, "test cleanup");
    }

    @Test
    void deterministicFailedRunIsNotRetried() {
        UUID projectId = insertProject();
        UUID runId = insertQueuedRun(projectId);

        dao.claimNextRun("worker-a");
        dao.finalizeRun(runId, RunStatus.FAILED, "Check assertions failed normally");

        assertThat(getRunStatus(runId)).isEqualTo("FAILED");

        var claimed = dao.claimNextRun("worker-a");
        assertThat(claimed).isEmpty();
    }

    private UUID insertProject() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO projects (id, slug, name) VALUES (?, ?, ?)",
                id, "slug-" + id, "name-" + id);
        return id;
    }

    private UUID insertQueuedRun(UUID projectId) {
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO runs (id, project_id, environment, status, config_hash) VALUES (?, ?, 'local', 'QUEUED', 'cfg')",
                runId, projectId);
        return runId;
    }

    private UUID insertRunInState(UUID projectId, String status, int attemptCount, Instant lockedAt, String lockedBy) {
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO runs (id, project_id, environment, status, config_hash, attempt_count, locked_at, locked_by)
                VALUES (?, ?, 'local', ?, 'cfg', ?, ?, ?)
                """,
                runId, projectId, status, attemptCount,
                lockedAt != null ? Timestamp.from(lockedAt) : null,
                lockedBy);
        return runId;
    }

    private UUID insertRunWithTimeout(UUID projectId, String status, int attemptCount,
                                      Instant lockedAt, Instant timeoutAt, String lockedBy) {
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO runs (id, project_id, environment, status, config_hash, attempt_count,
                                  locked_at, locked_by, timeout_at)
                VALUES (?, ?, 'local', ?, 'cfg', ?, ?, ?, ?)
                """,
                runId, projectId, status, attemptCount,
                Timestamp.from(lockedAt), lockedBy,
                Timestamp.from(timeoutAt));
        return runId;
    }

    private void insertCheck(UUID projectId, String key) {
        jdbcTemplate.update(
                """
                INSERT INTO checks (id, project_id, key, name, method, url, expected_status, config_hash, active)
                VALUES (gen_random_uuid(), ?, ?, ?, 'GET', 'http://localhost/test', 200, 'cfg', TRUE)
                """,
                projectId, key, key);
    }

    private void insertPartialResult(UUID runId) {
        jdbcTemplate.update(
                """
                INSERT INTO check_results (
                  id, run_id, check_id, check_key, check_name, status, actual_status, latency_ms, assertions_json, created_at
                ) VALUES (gen_random_uuid(), ?, NULL, 'partial', 'partial', 'ERROR', 500, 10, '[]'::jsonb, now())
                """,
                runId);
    }

    private String getRunStatus(UUID runId) {
        return jdbcTemplate.queryForObject("SELECT status FROM runs WHERE id = ?", String.class, runId);
    }

    private Instant minutesAgo(int minutes) {
        return Instant.now().minusSeconds(minutes * 60L);
    }
}
