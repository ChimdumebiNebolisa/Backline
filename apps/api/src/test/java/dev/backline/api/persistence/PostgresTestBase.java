package dev.backline.api.persistence;

import dev.backline.api.ApiApplication;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers PostgreSQL base for repository-level integration tests.
 *
 * <p>The container is started once via a static initializer so it stays alive for
 * the entire test JVM. This avoids Spring context caching conflicts that occur when
 * {@code @Container} stops/restarts the container per test class while Spring reuses
 * a cached context pointing at the old container port.
 *
 * <p>If Docker is not available (e.g. Docker Desktop not running on Windows), the
 * container is not started and all tests in subclasses are skipped with an
 * actionable message via JUnit 5 {@link Assumptions}.
 */
@SpringBootTest(classes = ApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class PostgresTestBase {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final boolean DOCKER_AVAILABLE;

    static {
        boolean started = false;
        try {
            POSTGRES.start();
            started = true;
        } catch (Exception e) {
            // Docker/Testcontainers not available in this environment
        }
        DOCKER_AVAILABLE = started;
    }

    @BeforeAll
    static void requireDockerForTests() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE,
                "Docker is not available — Testcontainers tests skipped. "
                        + "Start Docker Desktop and retry. See README.md troubleshooting.");
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
