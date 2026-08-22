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

    private PostgresTestContainers() {}

    public static PostgreSQLContainer<?> postgres() {
        return POSTGRES;
    }

    public static void requireDocker() {
        if (!DOCKER_AVAILABLE) {
            String cause = DOCKER_FAILURE == null ? "unknown" : DOCKER_FAILURE;
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                throw new IllegalStateException(
                        "Docker is required for Testcontainers tests in CI. "
                                + "Enable Docker on the CI runner and retry. Cause: " + cause);
            }
            String detail = "Docker/Testcontainers unavailable — PostgreSQL integration tests skipped. "
                    + "Start Docker Desktop and retry. See README.md troubleshooting. Cause: " + cause;
            // Gradle's XML/console does not surface assumption reasons, so print once per call.
            System.err.println("[test-skip] " + detail);
            Assumptions.assumeTrue(false, detail);
        }
    }
}
