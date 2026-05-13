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
}
