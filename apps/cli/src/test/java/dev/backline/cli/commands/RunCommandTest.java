package dev.backline.cli.commands;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.backline.cli.Backline;
import dev.backline.cli.TestDirs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RunCommandTest {

    @BeforeEach
    void clean() throws Exception {
        TestDirs.wipeDefaultWorkDir();
    }

    @Test
    void runNoWaitPrintsRunId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", RunCommandTest::handle);
        server.setExecutor(null);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            String yaml =
                    """
                    project: demo
                    environment: local
                    checks:
                      - key: k
                        name: n
                        method: GET
                        url: http://localhost:8081/health
                        expected_status: 200
                    """;
            Files.writeString(Path.of("backline.yml"), yaml);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintStream oldOut = System.out;
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            try {
                int code = new CommandLine(new Backline())
                        .execute("--api-url", base, "run", "-f", "backline.yml", "--no-wait");
                assertThat(code).isZero();
                assertThat(out.toString(StandardCharsets.UTF_8)).contains("RUN_ID: 22222222-2222-2222-2222-222222222222");
            } finally {
                System.setOut(oldOut);
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void runRejectsNonPositiveTimeoutBeforeReadingConfig() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            int code = new CommandLine(new Backline()).execute("run", "--timeout-seconds", "0");

            assertThat(code).isEqualTo(2);
            assertThat(err.toString(StandardCharsets.UTF_8)).contains("--timeout-seconds must be greater than zero");
        } finally {
            System.setErr(oldErr);
        }
    }

    @Test
    void runEnforcePolicyReturnsViolationExitCode() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", RunCommandTest::handleWithPolicyFailure);
        server.setExecutor(null);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            String yaml =
                    """
                    project: demo
                    environment: local
                    checks:
                      - key: k
                        name: n
                        method: GET
                        url: http://localhost:8081/health
                        expected_status: 200
                    policy:
                      max_newly_failing: 0
                    """;
            Files.writeString(Path.of("backline.yml"), yaml);
            int code = new CommandLine(new Backline())
                    .execute("--api-url", base, "run", "-f", "backline.yml", "--no-wait", "--enforce-policy");
            assertThat(code).isEqualTo(5);
        } finally {
            server.stop(0);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (var in = exchange.getRequestBody()) {
            in.readAllBytes();
        }
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if ("POST".equals(method) && path.equals("/api/projects")) {
            respond(
                    exchange,
                    201,
                    "{\"data\":{\"id\":\"11111111-1111-1111-1111-111111111111\",\"slug\":\"demo\",\"name\":\"demo\",\"createdAt\":\"2025-01-01T00:00:00Z\",\"updatedAt\":\"2025-01-01T00:00:00Z\"}}");
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

    private static void handleWithPolicyFailure(HttpExchange exchange) throws IOException {
        try (var in = exchange.getRequestBody()) {
            in.readAllBytes();
        }
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if ("POST".equals(method) && path.equals("/api/projects")) {
            respond(
                    exchange,
                    201,
                    "{\"data\":{\"id\":\"11111111-1111-1111-1111-111111111111\",\"slug\":\"demo\",\"name\":\"demo\",\"createdAt\":\"2025-01-01T00:00:00Z\",\"updatedAt\":\"2025-01-01T00:00:00Z\"}}");
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
                    "{\"data\":{\"id\":\"22222222-2222-2222-2222-222222222222\",\"projectId\":\"11111111-1111-1111-1111-111111111111\",\"environment\":\"local\",\"status\":\"FAILED\",\"configHash\":\"hash\",\"source\":\"cli\",\"queuedAt\":\"2025-01-01T00:00:00Z\",\"attemptCount\":0}}");
            return;
        }
        if ("GET".equals(method) && path.equals("/api/runs/22222222-2222-2222-2222-222222222222/results")) {
            respond(
                    exchange,
                    200,
                    "{\"data\":[{\"id\":\"r\",\"runId\":\"22222222-2222-2222-2222-222222222222\",\"checkId\":\"c\",\"checkKey\":\"k\",\"checkName\":\"n\",\"status\":\"FAILED\",\"actualStatus\":500,\"latencyMs\":10,\"assertions\":[],\"createdAt\":\"2025-01-01T00:00:00Z\"}]}");
            return;
        }
        if ("GET".equals(method) && path.equals("/api/runs/22222222-2222-2222-2222-222222222222/diff")) {
            respond(
                    exchange,
                    200,
                    "{\"data\":{\"runId\":\"22222222-2222-2222-2222-222222222222\",\"previousRunId\":\"11111111-1111-1111-1111-111111111111\",\"entries\":[{\"checkKey\":\"k\",\"checkName\":\"n\",\"changeType\":\"NEWLY_FAILING\",\"previousStatus\":\"PASSED\",\"currentStatus\":\"FAILED\",\"previousActualStatus\":200,\"currentActualStatus\":500,\"previousLatencyMs\":10,\"currentLatencyMs\":10}]}}");
            return;
        }
        exchange.sendResponseHeaders(404, -1);
    }
}
