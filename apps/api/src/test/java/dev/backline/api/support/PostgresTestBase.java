package dev.backline.api.support;

import dev.backline.api.ApiApplication;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Canonical Spring Boot + shared Testcontainers PostgreSQL base for all API integration tests.
 *
 * <p>Uses {@link SpringBootTest.WebEnvironment#RANDOM_PORT} so HTTP-layer tests can use
 * {@link org.springframework.boot.test.web.client.TestRestTemplate}. Repository and service
 * tests share the same base (Q8) to avoid a second container lifecycle and duplicate base class.
 */
@SpringBootTest(classes = ApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PostgresTestBase {

    @BeforeAll
    static void requireDockerForTests() {
        PostgresTestContainers.requireDocker();
    }

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestContainers::jdbcUrl);
        registry.add("spring.datasource.username", PostgresTestContainers::username);
        registry.add("spring.datasource.password", PostgresTestContainers::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> "false");
    }
}
