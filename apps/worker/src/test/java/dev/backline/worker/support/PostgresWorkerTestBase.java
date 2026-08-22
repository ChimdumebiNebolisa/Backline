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
    private static final boolean DOCKER_AVAILABLE;
    private static final String DOCKER_FAILURE;

    static {
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

    @BeforeAll
    static void requireDockerForTests() {
        if (!DOCKER_AVAILABLE) {
            String detail = "Docker/Testcontainers unavailable — PostgreSQL integration tests skipped. "
                    + "Start Docker Desktop and retry. See README.md troubleshooting. Cause: "
                    + (DOCKER_FAILURE == null ? "unknown" : DOCKER_FAILURE);
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                throw new IllegalStateException(
                        "Docker is required for Testcontainers tests in CI. "
                                + "Enable Docker on the CI runner and retry. Cause: "
                                + (DOCKER_FAILURE == null ? "unknown" : DOCKER_FAILURE));
            }
            // Gradle's XML/console does not surface assumption reasons, so print once per JVM.
            System.err.println("[test-skip] " + detail);
            Assumptions.assumeTrue(false, detail);
        }
    }
    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
