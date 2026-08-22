package dev.backline.cli.commands;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.backline.cli.Backline;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for {@code backline cancel}: success prints the cancelled run id and exit 0;
 * cancelling an already-terminal run surfaces the API's 409 as an actionable message with
 * exit 1.
 */
class CancelCommandTest {

    private static final String RUN_ID = "33333333-3333-3333-3333-333333333333";

    @Test
    void cancelPrintsRunIdAndStatusOnSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", CancelCommandTest::handleCancelSuccess);
        server.setExecutor(null);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintStream oldOut = System.out;
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            try {
                int code = new CommandLine(new Backline())
                        .execute("--api-url", base, "cancel", RUN_ID);
                assertThat(code).isZero();
                assertThat(out.toString(StandardCharsets.UTF_8))
                        .contains("cancelled run " + RUN_ID)
                        .contains("CANCELLED");
            } finally {
                System.setOut(oldOut);
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cancelTerminalRunPrintsActionableConflictMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", CancelCommandTest::handleCancelConflict);
        server.setExecutor(null);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            PrintStream oldErr = System.err;
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            try {
                int code = new CommandLine(new Backline())
                        .execute("--api-url", base, "cancel", RUN_ID);
                assertThat(code).isEqualTo(1);
                assertThat(err.toString(StandardCharsets.UTF_8))
                        .contains("Cannot cancel run")
                        .contains("QUEUED or RUNNING");
            } finally {
                System.setErr(oldErr);
            }
        } finally {
            server.stop(0);
        }
    }

    private static void handleCancelSuccess(HttpExchange exchange) throws IOException {
        byte[] body = ("{\"data\":{\"id\":\"" + RUN_ID + "\",\"status\":\"CANCELLED\","
                + "\"environment\":\"local\",\"configHash\":\"cfg\",\"attemptCount\":1}}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static void handleCancelConflict(HttpExchange exchange) throws IOException {
        byte[] body = ("{\"error\":{\"code\":\"CONFLICT\",\"message\":\"run already finished "
                + "with status PASSED\",\"field\":\"runId\"}}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(409, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
