package dev.backline.cli.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.backline.core.api.dto.CheckSyncRequest;
import dev.backline.core.api.dto.CreateProjectRequest;
import dev.backline.core.api.dto.CreateRunRequest;
import dev.backline.core.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiClientTest {

    private final AtomicInteger projectCalls = new AtomicInteger();

    @Test
    void roundTripsCoreEndpoints() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            BacklineApiClient client = new BacklineApiClient(base);
            var project = client.createProject(new CreateProjectRequest("demo", "demo"));
            assertThat(project.slug()).isEqualTo("demo");

            assertThatThrownBy(() -> client.createProject(new CreateProjectRequest("demo", "demo")))
                    .isInstanceOfSatisfying(ApiClientException.class, ex -> {
                        assertThat(ex.httpStatus()).isEqualTo(409);
                        assertThat(ex.code()).isEqualTo(ErrorCode.CONFLICT);
                    });

            var checks = client.syncChecks(new CheckSyncRequest("demo", "demo", java.util.List.of()));
            assertThat(checks).isEmpty();

            var run = client.submitRun(new CreateRunRequest("demo", "local", "hash", null, "cli"));
            assertThat(run.id()).isEqualTo("22222222-2222-2222-2222-222222222222");

            var fetched = client.getRun(UUID.fromString(run.id()));
            assertThat(fetched.status().name()).isEqualTo("QUEUED");

            var diff = client.getRunDiff(UUID.fromString(run.id()));
            assertThat(diff.runId()).isEqualTo(run.id());
            assertThat(diff.entries()).hasSize(1);
        } finally {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (var in = exchange.getRequestBody()) {
            in.readAllBytes();
        }
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if ("POST".equals(method) && path.equals("/api/projects")) {
            int n = projectCalls.incrementAndGet();
            if (n == 1) {
                respond(
                        exchange,
                        201,
                        "{\"data\":{\"id\":\"11111111-1111-1111-1111-111111111111\",\"slug\":\"demo\",\"name\":\"demo\",\"createdAt\":\"2025-01-01T00:00:00Z\",\"updatedAt\":\"2025-01-01T00:00:00Z\"}}");
            } else {
                respond(
                        exchange,
                        409,
                        "{\"error\":{\"code\":\"CONFLICT\",\"message\":\"exists\",\"field\":\"slug\"}}");
            }
            return;
        }
        if ("POST".equals(method) && path.equals("/api/checks/sync")) {
            respond(exchange, 200, "{\"data\":[]}");
            return;
        }
        if ("POST".equals(method) && path.equals("/api/runs")) {
            respond(
                    exchange,
                    201,
                    "{\"data\":{\"id\":\"22222222-2222-2222-2222-222222222222\",\"projectId\":\"11111111-1111-1111-1111-111111111111\",\"environment\":\"local\",\"status\":\"QUEUED\",\"configHash\":\"hash\",\"source\":\"cli\",\"queuedAt\":\"2025-01-01T00:00:00Z\",\"attemptCount\":0}}");
            return;
        }
        if ("GET".equals(method) && path.equals("/api/runs/22222222-2222-2222-2222-222222222222")) {
            respond(
                    exchange,
                    200,
                    "{\"data\":{\"id\":\"22222222-2222-2222-2222-222222222222\",\"projectId\":\"11111111-1111-1111-1111-111111111111\",\"environment\":\"local\",\"status\":\"QUEUED\",\"configHash\":\"hash\",\"source\":\"cli\",\"queuedAt\":\"2025-01-01T00:00:00Z\",\"attemptCount\":0}}");
            return;
        }
        if ("GET".equals(method) && path.equals("/api/runs/22222222-2222-2222-2222-222222222222/diff")) {
            respond(
                    exchange,
                    200,
                    "{\"data\":{\"runId\":\"22222222-2222-2222-2222-222222222222\",\"previousRunId\":null,\"entries\":[{\"checkKey\":\"k\",\"checkName\":\"n\",\"changeType\":\"NEWLY_ADDED\",\"previousStatus\":null,\"currentStatus\":\"PASSED\",\"previousActualStatus\":null,\"currentActualStatus\":200,\"previousLatencyMs\":null,\"currentLatencyMs\":10}]}}");
            return;
        }
        exchange.sendResponseHeaders(404, -1);
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
