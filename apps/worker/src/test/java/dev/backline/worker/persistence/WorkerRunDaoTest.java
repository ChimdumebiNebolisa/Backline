package dev.backline.worker.persistence;

import dev.backline.core.run.RunStatus;
import dev.backline.worker.support.PostgresWorkerTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerRunDaoTest extends PostgresWorkerTestBase {

    @Autowired
    private WorkerRunDao dao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void claimReturnsRowThenEmptyWhenNoQueuedRuns() {
        UUID projectId = insertProject();
        UUID runId = insertQueuedRun(projectId);

        assertThat(dao.claimNextRun("worker-a")).isPresent().get().satisfies(run -> assertThat(run.runId()).isEqualTo(runId));

        assertThat(dao.claimNextRun("worker-a")).isEmpty();

        dao.finalizeRun(runId, RunStatus.PASSED);
    }

    @Test
    void requeueReturnsRunToQueuedWithFutureNextAttempt() throws InterruptedException {
        UUID projectId = insertProject();
        UUID runId = insertQueuedRun(projectId);
        UUID checkId = insertCheck(projectId, "requeue-check");

        dao.claimNextRun("worker-a");
        dao.writeCheckResult(runId, new CheckResultRow(
                checkId,
                "requeue-check",
                "Requeue Check",
                dev.backline.core.check.CheckResultStatus.ERROR,
                500,
                20L,
                "ERR",
                "boom",
                "{}",
                "[]"));
        dao.requeueForRetry(runId, 50);

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM runs WHERE id = ?", String.class, runId))
                .isEqualTo("QUEUED");
        Integer resultsAfterRequeue =
                jdbcTemplate.queryForObject("SELECT count(*) FROM check_results WHERE run_id = ?", Integer.class, runId);
        assertThat(resultsAfterRequeue).isZero();

        Thread.sleep(75);

        var reclaimed = dao.claimNextRun("worker-b");
        assertThat(reclaimed).isPresent();
        assertThat(reclaimed.orElseThrow().runId()).isEqualTo(runId);

        dao.finalizeRun(runId, RunStatus.ERROR, "cleanup");
    }

    @Test
    void finalizeRunRejectsWhenRunNotRunning() {
        UUID projectId = insertProject();
        UUID runId = insertQueuedRun(projectId);

        assertThatThrownBy(() -> dao.finalizeRun(runId, RunStatus.PASSED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNNING");
    }

    @Test
    void persistResultsAndFinalizeIsAtomicForTerminalUpdate() {
        UUID projectId = insertProject();
        UUID runId = insertQueuedRun(projectId);
        UUID checkId = insertCheck(projectId, "k");
        dao.claimNextRun("worker-a");

        CheckResultRow row = new CheckResultRow(
                checkId,
                "k",
                "K",
                dev.backline.core.check.CheckResultStatus.PASSED,
                200,
                12L,
                null,
                null,
                "{}",
                "[]");
        dao.persistResultsAndFinalize(runId, List.of(row), RunStatus.PASSED);

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM runs WHERE id = ?", String.class, runId))
                .isEqualTo("PASSED");
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM check_results WHERE run_id = ?", Integer.class, runId);
        assertThat(count).isEqualTo(1);
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

    private UUID insertCheck(UUID projectId, String key) {
        UUID checkId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO checks (
                          id, project_id, key, name, method, url, expected_status, max_latency_ms, assertions_json, config_hash, active
                        ) VALUES (?, ?, ?, ?, 'GET', 'http://localhost:8081/health', 200, NULL, NULL, 'cfg', TRUE)
                        """,
                checkId,
                projectId,
                key,
                key);
        return checkId;
    }
}
