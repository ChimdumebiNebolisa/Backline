package dev.backline.api.persistence;

import dev.backline.api.ApiApplication;
import dev.backline.api.support.PostgresTestContainers;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Spring Boot + shared Testcontainers PostgreSQL for repository and service integration tests
 * without a servlet stack.
 */
@SpringBootTest(classes = ApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class PostgresTestBase {

    @BeforeAll
    static void requireDockerForTests() {
        PostgresTestContainers.requireDocker();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        var postgres = PostgresTestContainers.postgres();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
