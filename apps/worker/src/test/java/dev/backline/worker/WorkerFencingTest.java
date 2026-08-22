package dev.backline.worker;

import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.run.RunStatus;
import dev.backline.worker.persistence.CheckResultRow;
import dev.backline.worker.persistence.WorkerRunDao;
import dev.backline.worker.support.PostgresWorkerTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for claim-ownership fencing: after a stale-recovery requeue and re-claim by
 * another worker, the original owner must not be able to finalize, requeue, or persist results
 * for the run (audit finding F-001, proven against PostgreSQL before the fix).
 */
class WorkerFencingTest extends PostgresWorkerTestBase {

    @Autowired
    private WorkerRunDao dao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void staleOwnerCannotFinalizeAfterAnotherWorkerReclaimed() {
        deleteStrayNonTerminalRuns(jdbcTemplate);
        UUID projectId = insertProject();
        UUID runId = insertQueuedRun(projectId);

        var firstClaim = dao.claimNextRun("worker-a");
        assertThat(firstClaim).isPresent();
        makeStale(runId);
        // Zero backoff so the recovered run is immediately claimable; otherwise the
        // re-claim below races the scheduled retry window nondeterministically.
        assertThat(dao.recoverStaleRuns(60_000, 3, 0)).isEqualTo(1);

        var secondClaim = dao.claimNextRun("worker-b");
        assertThat(secondClaim).isPresent().get().satisfies(run -> {
            assertThat(run.runId()).isEqualTo(runId);
            assertThat(run.lockedBy()).isEqualTo("worker-b");
        });

        assertThat(dao.finalizeRun(runId, RunStatus.PASSED, "stale worker-a", "worker-a")).isFalse();
        assertOwnedBy(runId, "worker-b");
    }

    @Test
    void staleOwnerCannotPersistResultsOrRequeueAfterAnotherWorkerReclaimed() {
        deleteStrayNonTerminalRuns(jdbcTemplate);
        UUID projectId = insertProject();
        UUID runId = insertQueuedRun(projectId);

        assertThat(dao.claimNextRun("worker-a")).isPresent();
        makeStale(runId);
        assertThat(dao.recoverStaleRuns(60_000, 3, 0)).isEqualTo(1);
        assertThat(dao.claimNextRun("worker-b")).isPresent();

        List<CheckResultRow> rows = List.of(new CheckResultRow(
                null, "k", "K", CheckResultStatus.PASSED, 200, 10L, null, null, "{}", "[]"));

        assertThat(dao.persistResultsAndFinalize(runId, rows, RunStatus.PASSED, "worker-a")).isFalse();
        Integer results = resultCount(runId);
        assertThat(results).isZero();
        assertOwnedBy(runId, "worker-b");

        assertThat(dao.requeueForRetry(runId, 0, "worker-a")).isFalse();
        assertOwnedBy(runId, "worker-b");

        // The current owner still completes normally.
        assertThat(dao.persistResultsAndFinalize(runId, rows, RunStatus.PASSED, "worker-b")).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM runs WHERE id = ?", String.class, runId))
                .isEqualTo("PASSED");
        assertThat(resultCount(runId)).isEqualTo(1);
    }

    @Test
    void cancelledRunningRunCannotBeFinalizedRequeuedOrPersistedByOwner() {
        deleteStrayNonTerminalRuns(jdbcTemplate);
        UUID projectId = insertProject();
        UUID runId = insertQueuedRun(projectId);

        assertThat(dao.claimNextRun("worker-a")).isPresent();
        // Simulates the API's conditional cancel update winning over this worker.
        jdbcTemplate.update(
                "UPDATE runs SET status = 'CANCELLED', finished_at = now(), locked_by = NULL, "
                        + "locked_at = NULL, timeout_at = NULL WHERE id = ? AND status = 'RUNNING'",
                runId);

        List<CheckResultRow> rows = List.of(new CheckResultRow(
                null, "k", "K", CheckResultStatus.PASSED, 200, 10L, null, null, "{}", "[]"));

        assertThat(dao.persistResultsAndFinalize(runId, rows, RunStatus.PASSED, "worker-a")).isFalse();
        assertThat(dao.finalizeRun(runId, RunStatus.FAILED, "late finalize", "worker-a")).isFalse();
        assertThat(dao.requeueForRetry(runId, 0, "worker-a")).isFalse();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM runs WHERE id = ?", String.class, runId))
                .isEqualTo("CANCELLED");
        assertThat(resultCount(runId)).isZero();
    }

    private void makeStale(UUID runId) {
        jdbcTemplate.update("UPDATE runs SET locked_at = now() - interval '1 hour' WHERE id = ?", runId);
    }

    private void assertOwnedBy(UUID runId, String expectedOwner) {
        var state = jdbcTemplate.queryForObject(
                "SELECT status || '/' || coalesce(locked_by, '-') FROM runs WHERE id = ?",
                String.class,
                runId);
        assertThat(state).isEqualTo("RUNNING/" + expectedOwner);
    }

    private Integer resultCount(UUID runId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM check_results WHERE run_id = ?", Integer.class, runId);
    }

    private UUID insertProject() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO projects (id, slug, name) VALUES (?, ?, ?)",
                id,
                "slug-" + id,
                "name-" + id);
        return id;
    }

    private UUID insertQueuedRun(UUID projectId) {
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO runs (id, project_id, environment, status, config_hash)
                        VALUES (?, ?, 'local', 'QUEUED', 'cfg')
                        """,
                runId,
                projectId);
        return runId;
    }
}
