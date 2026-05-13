package dev.backline.worker;

import dev.backline.core.run.RunStatus;
import dev.backline.worker.persistence.WorkerRunDao;
import dev.backline.worker.support.PostgresWorkerTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerClaimConcurrencyTest extends PostgresWorkerTestBase {

    @Autowired
    private WorkerRunDao dao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void onlyOneWorkerClaimsEachQueuedRun() throws Exception {
        UUID projectId = insertProject();
        List<UUID> runIds = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            runIds.add(insertQueuedRun(projectId));
        }

        AtomicInteger processed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            int workerIdx = t;
            futures.add(
                    pool.submit(
                            () -> {
                                long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);
                                while (processed.get() < 50 && System.currentTimeMillis() < deadline) {
                                    var claimed = dao.claimNextRun("worker-" + workerIdx);
                                    if (claimed.isPresent()) {
                                        dao.finalizeRun(claimed.orElseThrow().runId(), RunStatus.ERROR, "concurrency test");
                                        processed.incrementAndGet();
                                    } else {
                                        Thread.sleep(2);
                                    }
                                }
                            }));
        }

        for (Future<?> future : futures) {
            future.get(2, TimeUnit.MINUTES);
        }
        pool.shutdownNow();

        assertThat(processed.get()).isEqualTo(50);
        Long errorRuns =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM runs WHERE status = 'ERROR'", Long.class);
        assertThat(errorRuns).isEqualTo(50L);
        Long running =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM runs WHERE status = 'RUNNING'", Long.class);
        assertThat(running).isZero();
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
