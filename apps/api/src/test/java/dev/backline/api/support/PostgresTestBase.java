package dev.backline.api.support;

import dev.backline.api.ApiApplication;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Spring Boot + Testcontainers PostgreSQL for HTTP-layer API tests ({@link org.springframework.boot.test.web.client.TestRestTemplate}).
 *
 * <p>Repository-focused tests use {@code dev.backline.api.persistence.PostgresTestBase} (Task 2) with
 * {@link SpringBootTest.WebEnvironment#NONE}. Web tests keep this separate base so a random port and full servlet
 * stack are available without modifying Task 2-owned test infrastructure.
 *
 * <p>If Docker is not available, all tests in subclasses are skipped gracefully.
 */
@SpringBootTest(classes = ApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PostgresTestBase {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
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
        if (!DOCKER_AVAILABLE) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                throw new IllegalStateException(
                        "Docker is required for Testcontainers tests in CI. "
                                + "Enable Docker on the CI runner and retry.");
            }
            Assumptions.assumeTrue(false,
                    "Docker is not available — Testcontainers tests skipped. "
                            + "Start Docker Desktop and retry. See README.md troubleshooting.");
        }
    }

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> "false");
    }
}
