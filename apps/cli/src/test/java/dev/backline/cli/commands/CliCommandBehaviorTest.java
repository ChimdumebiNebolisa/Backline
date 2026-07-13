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

import static org.assertj.core.api.Assertions.assertThat;

class CliCommandBehaviorTest {

    private static final String RUN_ID = "22222222-2222-2222-2222-222222222222";

    @BeforeEach
    void clean() throws Exception {
        TestDirs.wipeDefaultWorkDir();
    }

    @Test
    void statusReturnsExitCodeThreeForCancelledRun() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", CliCommandBehaviorTest::handleCancelledRun);
        server.setExecutor(null);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            int code = new CommandLine(new Backline()).execute("--api-url", base, "status", RUN_ID);
            assertThat(code).isEqualTo(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void diffPrintsClearMessageWhenNoBaselineExists() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", CliCommandBehaviorTest::handleDiffNoBaseline);
        server.setExecutor(null);
        server.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            int code = new CommandLine(new Backline()).execute("--api-url", base, "diff", RUN_ID);
            assertThat(code).isZero();
            String stdout = out.toString(StandardCharsets.UTF_8);
            assertThat(stdout).contains("no baseline run found");
            assertThat(stdout).doesNotContain("vs null");
        } finally {
            System.setOut(oldOut);
            server.stop(0);
        }
    }

    private static void handleCancelledRun(HttpExchange exchange) throws IOException {
        try (var in = exchange.getRequestBody()) {
            in.readAllBytes();
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/runs/" + RUN_ID)) {
            respond(
                    exchange,
                    200,
                    "{\"data\":{\"id\":\"" + RUN_ID + "\",\"projectId\":\"11111111-1111-1111-1111-111111111111\","
                            + "\"environment\":\"local\",\"status\":\"CANCELLED\",\"configHash\":\"h\",\"source\":\"cli\","
                            + "\"queuedAt\":\"2025-01-01T00:00:00Z\",\"attemptCount\":0}}");
            return;
        }
        if (path.equals("/api/runs/" + RUN_ID + "/results")) {
            respond(exchange, 200, "{\"data\":[]}");
            return;
        }
        exchange.sendResponseHeaders(404, -1);
    }

    private static void handleDiffNoBaseline(HttpExchange exchange) throws IOException {
        try (var in = exchange.getRequestBody()) {
            in.readAllBytes();
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/runs/" + RUN_ID + "/diff")) {
            respond(
                    exchange,
                    200,
                    "{\"data\":{\"runId\":\"" + RUN_ID + "\",\"previousRunId\":null,\"entries\":[]}}");
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
