package dev.backline.api.support;

import org.junit.jupiter.api.Assumptions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers PostgreSQL lifecycle for API integration tests.
 *
 * <p>Both repository-focused and web-layer test bases use this single container so only one
 * Postgres instance starts per test JVM.
 *
 * <p>When {@code BACKLINE_TEST_JDBC_URL} (plus {@code BACKLINE_TEST_DB_USER} and
 * {@code BACKLINE_TEST_DB_PASSWORD}) is set, no container is started: the suites run against
 * that already-running PostgreSQL instead. This exists for environments where the Testcontainers
 * Docker client cannot reach the local daemon even though Docker itself works; CI always uses
 * Testcontainers.
 */
public final class PostgresTestContainers {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    private static final boolean EXTERNAL_DB;
    private static final String EXTERNAL_JDBC_URL;
    private static final String EXTERNAL_DB_USER;
    private static final String EXTERNAL_DB_PASSWORD;

    private static final boolean DOCKER_AVAILABLE;
    private static final String DOCKER_FAILURE;

    static {
        String url = System.getenv("BACKLINE_TEST_JDBC_URL");
        String user = System.getenv("BACKLINE_TEST_DB_USER");
        String password = System.getenv("BACKLINE_TEST_DB_PASSWORD");
        EXTERNAL_DB = url != null && !url.isBlank()
                && user != null && !user.isBlank()
                && password != null && !password.isBlank();
        String failure = null;
        if (EXTERNAL_DB) {
            EXTERNAL_JDBC_URL = url;
            EXTERNAL_DB_USER = user;
            EXTERNAL_DB_PASSWORD = password;
        } else {
            EXTERNAL_JDBC_URL = null;
            EXTERNAL_DB_USER = null;
            EXTERNAL_DB_PASSWORD = null;
            try {
                POSTGRES.start();
            } catch (Exception e) {
                // Keep the root cause visible: a silent skip here looks identical to a green
                // build and hides misconfigured Docker environments (audit finding F-007).
                failure = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }
        DOCKER_AVAILABLE = EXTERNAL_DB || failure == null;
        DOCKER_FAILURE = failure;
    }

    private PostgresTestContainers() {}

    public static boolean usingExternalDatabase() {
        return EXTERNAL_DB;
    }

    public static String jdbcUrl() {
        return EXTERNAL_DB ? EXTERNAL_JDBC_URL : POSTGRES.getJdbcUrl();
    }

    public static String username() {
        return EXTERNAL_DB ? EXTERNAL_DB_USER : POSTGRES.getUsername();
    }

    public static String password() {
        return EXTERNAL_DB ? EXTERNAL_DB_PASSWORD : POSTGRES.getPassword();
    }

    public static void requireDocker() {
        if (EXTERNAL_DB || DOCKER_AVAILABLE) {
            return;
        }
        String cause = DOCKER_FAILURE == null ? "unknown" : DOCKER_FAILURE;
        if ("true".equalsIgnoreCase(System.getenv("CI"))) {
            throw new IllegalStateException(
                    "Docker is required for Testcontainers tests in CI. "
                            + "Enable Docker on the CI runner and retry. Cause: " + cause);
        }
        String detail = "Docker/Testcontainers unavailable — PostgreSQL integration tests skipped. "
                + "Start Docker Desktop or set BACKLINE_TEST_JDBC_URL to run against an existing "
                + "PostgreSQL. See README.md troubleshooting. Cause: " + cause;
        // Gradle's XML/console does not surface assumption reasons, so print once per call.
        System.err.println("[test-skip] " + detail);
        Assumptions.assumeTrue(false, detail);
    }
}
