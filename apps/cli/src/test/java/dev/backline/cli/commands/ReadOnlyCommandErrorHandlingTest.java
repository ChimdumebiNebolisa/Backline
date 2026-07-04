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
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOnlyCommandErrorHandlingTest {

    private static final String RUN_ID = "00000000-0000-0000-0000-000000000000";

    @BeforeEach
    void clean() throws Exception {
        TestDirs.wipeDefaultWorkDir();
    }

    @Test
    void historyReportsUnreachableApiWithoutStackTrace() throws Exception {
        assertUnreachableApiMessage("history");
    }

    @Test
    void statusReportsUnreachableApiWithoutStackTrace() throws Exception {
        assertUnreachableApiMessage("status", RUN_ID);
    }

    @Test
    void diffReportsUnreachableApiWithoutStackTrace() throws Exception {
        assertUnreachableApiMessage("diff", RUN_ID);
    }

    @Test
    void reportReportsUnreachableApiWithoutStackTrace() throws Exception {
        assertUnreachableApiMessage("report", RUN_ID);
    }

    @Test
    void statusReportsStructuredApiErrorWithoutStackTrace() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", ReadOnlyCommandErrorHandlingTest::handleNotFound);
        server.setExecutor(null);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            CommandResult result = execute("--api-url", base, "status", RUN_ID);

            assertThat(result.code()).isEqualTo(1);
            assertThat(result.err()).contains("API error (404): run not found");
            assertThat(result.err()).doesNotContain("java.net").doesNotContain("\tat ");
        } finally {
            server.stop(0);
        }
    }

    private static void assertUnreachableApiMessage(String... commandArgs) throws Exception {
        String base = "http://127.0.0.1:" + closedPort();
        String[] args = new String[commandArgs.length + 2];
        args[0] = "--api-url";
        args[1] = base;
        System.arraycopy(commandArgs, 0, args, 2, commandArgs.length);

        CommandResult result = execute(args);

        assertThat(result.code()).isEqualTo(1);
        assertThat(result.err()).contains("Cannot reach API at " + base);
        assertThat(result.err()).doesNotContain("java.net").doesNotContain("\tat ");
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static CommandResult execute(String... args) {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            int code = new CommandLine(new Backline()).execute(args);
            return new CommandResult(code, err.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(oldErr);
        }
    }

    private static void handleNotFound(HttpExchange exchange) throws IOException {
        try (var in = exchange.getRequestBody()) {
            in.readAllBytes();
        }
        respond(exchange, 404, "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"run not found\",\"field\":\"runId\"}}");
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private record CommandResult(int code, String err) {
    }
}
