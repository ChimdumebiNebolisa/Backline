package dev.backline.api.support;

import org.junit.jupiter.api.Assumptions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers PostgreSQL lifecycle for API integration tests.
 *
 * <p>Both repository-focused and web-layer test bases use this single container so only one
 * Postgres instance starts per test JVM.
 */
public final class PostgresTestContainers {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

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

    private PostgresTestContainers() {}

    public static PostgreSQLContainer<?> postgres() {
        return POSTGRES;
    }

    public static void requireDocker() {
        if (!DOCKER_AVAILABLE) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                throw new IllegalStateException(
                        "Docker is required for Testcontainers tests in CI. "
                                + "Enable Docker on the CI runner and retry.");
            }
            Assumptions.assumeTrue(
                    false,
                    "Docker is not available — Testcontainers tests skipped. "
                            + "Start Docker Desktop and retry. See README.md troubleshooting.");
        }
    }
}
