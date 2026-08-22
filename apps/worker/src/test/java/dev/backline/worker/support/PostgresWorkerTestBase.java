package dev.backline.worker.support;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import dev.backline.worker.WorkerApplication;

/**
 * Spins up PostgreSQL with Testcontainers, wires Spring JDBC, and applies Flyway migrations from
 * {@code classpath:db/migration} using the test profile.
 *
 * <p>The container is started once via a static initializer so it stays alive for the entire
 * test JVM. This avoids Spring context caching conflicts that occur when {@code @Container}
 * stops/restarts the container per test class while Spring reuses a cached context pointing
 * at the old container port.
 *
 * <p>If Docker is not available, all tests in subclasses are skipped gracefully.
 */
@SpringBootTest(classes = WorkerApplication.class)
@ActiveProfiles("test")
public abstract class PostgresWorkerTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final boolean EXTERNAL_DB;
    private static final String EXTERNAL_JDBC_URL;
    private static final String EXTERNAL_DB_USER;
    private static final String EXTERNAL_DB_PASSWORD;
    private static final boolean DOCKER_AVAILABLE;
    private static final String DOCKER_FAILURE;

    static {
        String url = System.getenv("BACKLINE_TEST_JDBC_URL");
        String user = System.getenv("BACKLINE_TEST_DB_USER");
        String password = System.getenv("BACKLINE_TEST_DB_PASSWORD");
        EXTERNAL_DB = url != null && !url.isBlank()
                && user != null && !user.isBlank()
                && password != null && !password.isBlank();
        if (EXTERNAL_DB) {
            // Run against an already-started PostgreSQL; used only where the Testcontainers
            // client cannot reach the local daemon even though Docker itself works. CI uses
            // Testcontainers.
            EXTERNAL_JDBC_URL = url;
            EXTERNAL_DB_USER = user;
            EXTERNAL_DB_PASSWORD = password;
            DOCKER_AVAILABLE = true;
            DOCKER_FAILURE = null;
        } else {
            EXTERNAL_JDBC_URL = null;
            EXTERNAL_DB_USER = null;
            EXTERNAL_DB_PASSWORD = null;
            String failure = null;
            try {
                POSTGRES.start();
            } catch (Exception e) {
                // Keep the root cause visible: a silent skip here looks identical to a green
                // build and hides misconfigured Docker environments (audit finding F-007).
                failure = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            DOCKER_AVAILABLE = failure == null;
            DOCKER_FAILURE = failure;
        }
    }

    @BeforeAll
    static void requireDockerForTests() {
        if (EXTERNAL_DB || DOCKER_AVAILABLE) {
            return;
        }
        String cause = DOCKER_FAILURE == null ? "unknown" : DOCKER_FAILURE;
        String detail = "Docker/Testcontainers unavailable — PostgreSQL integration tests skipped. "
                + "Start Docker Desktop or set BACKLINE_TEST_JDBC_URL to run against an existing "
                + "PostgreSQL. See README.md troubleshooting. Cause: " + cause;
        if ("true".equalsIgnoreCase(System.getenv("CI"))) {
            throw new IllegalStateException(
                    "Docker is required for Testcontainers tests in CI. "
                            + "Enable Docker on the CI runner and retry. Cause: " + cause);
        }
        // Gradle's XML/console does not surface assumption reasons, so print once per JVM.
        System.err.println("[test-skip] " + detail);
        Assumptions.assumeTrue(false, detail);
    }

    /**
     * Deletes every queued/running run regardless of owner. Claim-sensitive tests must call
     * this first: {@code claimNextRun} consumes any queued run FIFO, so rows left behind by
     * earlier test classes (or previous suite runs against a persistent database) otherwise
     * get claimed before the fixture's own run.
     */
    protected static void deleteStrayNonTerminalRuns(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM runs WHERE status IN ('QUEUED', 'RUNNING')");
    }

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> EXTERNAL_DB ? EXTERNAL_JDBC_URL : POSTGRES.getJdbcUrl());
        registry.add("spring.datasource.username",
                () -> EXTERNAL_DB ? EXTERNAL_DB_USER : POSTGRES.getUsername());
        registry.add("spring.datasource.password",
                () -> EXTERNAL_DB ? EXTERNAL_DB_PASSWORD : POSTGRES.getPassword());
    }
}
