package dev.backline.api.persistence;

import dev.backline.api.ApiApplication;
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
 */
@SpringBootTest(classes = ApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class PostgresTestBase {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
