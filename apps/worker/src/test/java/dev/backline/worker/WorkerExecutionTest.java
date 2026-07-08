package dev.backline.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.run.RunStatus;
import dev.backline.executor.HttpCheckExecutor;
import dev.backline.worker.config.WorkerProperties;
import dev.backline.worker.loop.WorkerLoop;
import dev.backline.worker.persistence.WorkerRunDao;
import dev.backline.worker.support.PostgresWorkerTestBase;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerExecutionTest extends PostgresWorkerTestBase {

    @Autowired
    private WorkerRunDao dao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkerProperties workerProperties;

    @Autowired
    private HttpCheckExecutor httpCheckExecutor;

    @Autowired
    private ObjectMapper objectMapper;

    private MockWebServer server;
    private WorkerLoop loop;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void shutdown() throws IOException {
        if (loop != null) {
            loop.stop();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    void processesQueuedRunEndToEnd() throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if ("/pass".equals(path)) {
                    return new MockResponse().setBody("{\"id\":1}").addHeader("Content-Type", "application/json");
                }
                if ("/fail".equals(path)) {
                    return new MockResponse().setResponseCode(500);
                }
                if ("/assert".equals(path)) {
                    return new MockResponse().setBody("{\"id\":1}").addHeader("Content-Type", "application/json");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        UUID projectId = insertProject();
        UUID passCheck = insertCheck(
                projectId,
                "a-pass",
                "Passing",
                "GET",
                server.url("/pass").toString(),
                200,
                List.of(new AssertionDto("$.id", 1, null)));
        UUID failCheck =
                insertCheck(projectId, "b-fail", "Failing status", "GET", server.url("/fail").toString(), 200, null);
        UUID assertCheck = insertCheck(
                projectId,
                "c-assert",
                "Failing assertion",
                "GET",
                server.url("/assert").toString(),
                200,
                List.of(new AssertionDto("$.id", 2, null)));

        UUID runId = insertQueuedRun(projectId);

        loop = new WorkerLoop(workerProperties, dao, httpCheckExecutor, objectMapper);
        loop.start();

        waitForTerminal(runId, Duration.ofSeconds(90));

        String runStatus = jdbcTemplate.queryForObject("SELECT status FROM runs WHERE id = ?", String.class, runId);
        assertThat(runStatus).isIn(RunStatus.FAILED.name(), RunStatus.ERROR.name());

        String passStatus =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM check_results WHERE run_id = ? AND check_id = ?",
                        String.class,
                        runId,
                        passCheck);
        assertThat(passStatus).isEqualTo("PASSED");

        String failStatus =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM check_results WHERE run_id = ? AND check_id = ?",
                        String.class,
                        runId,
                        failCheck);
        assertThat(failStatus).isIn("FAILED", "ERROR");

        String assertStatus =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM check_results WHERE run_id = ? AND check_id = ?",
                        String.class,
                        runId,
                        assertCheck);
        assertThat(assertStatus).isIn("FAILED", "ERROR");
    }

    private void waitForTerminal(UUID runId, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            String status = jdbcTemplate.queryForObject("SELECT status FROM runs WHERE id = ?", String.class, runId);
            if (RunStatus.PASSED.name().equals(status)
                    || RunStatus.FAILED.name().equals(status)
                    || RunStatus.ERROR.name().equals(status)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Run did not reach a terminal status in time");
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

    private UUID insertCheck(
            UUID projectId,
            String key,
            String name,
            String method,
            String url,
            int expectedStatus,
            List<AssertionDto> assertions) throws Exception {
        UUID checkId = UUID.randomUUID();
        if (assertions == null || assertions.isEmpty()) {
            jdbcTemplate.update(
                    """
                            INSERT INTO checks (id, project_id, key, name, method, url, expected_status, max_latency_ms, assertions_json, config_hash, active)
                            VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, 'cfg', TRUE)
                            """,
                    checkId,
                    projectId,
                    key,
                    name,
                    method,
                    url,
                    expectedStatus);
        } else {
            String assertionsJson = objectMapper.writeValueAsString(assertions);
            jdbcTemplate.update(
                    """
                            INSERT INTO checks (id, project_id, key, name, method, url, expected_status, max_latency_ms, assertions_json, config_hash, active)
                            VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?::jsonb, 'cfg', TRUE)
                            """,
                    checkId,
                    projectId,
                    key,
                    name,
                    method,
                    url,
                    expectedStatus,
                    assertionsJson);
        }
        return checkId;
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
