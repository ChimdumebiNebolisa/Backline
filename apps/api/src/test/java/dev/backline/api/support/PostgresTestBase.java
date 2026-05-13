package dev.backline.api.support;

import dev.backline.api.ApiApplication;
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
 */
@SpringBootTest(classes = ApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PostgresTestBase {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
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
