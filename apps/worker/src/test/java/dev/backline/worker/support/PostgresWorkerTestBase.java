package dev.backline.worker.support;

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
 */
@SpringBootTest(classes = WorkerApplication.class)
@ActiveProfiles("test")
public abstract class PostgresWorkerTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
