package dev.backline.worker.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.backline.worker.WorkerApplication;

/**
 * Spins up PostgreSQL with Testcontainers, wires Spring JDBC, and applies Flyway migrations from
 * {@code classpath:db/migration} using the test profile.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = WorkerApplication.class)
@ActiveProfiles("test")
public abstract class PostgresWorkerTestBase {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
