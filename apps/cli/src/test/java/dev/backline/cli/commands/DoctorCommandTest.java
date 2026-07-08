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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorCommandTest {

    @Test
    void doctorFailsWhenApiUnreachableWithRemediationHint() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream old = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            int code = new CommandLine(new Backline()).execute("--api-url", "http://127.0.0.1:1", "doctor");
            assertThat(code).isEqualTo(1);
            String text = out.toString(StandardCharsets.UTF_8);
            assertThat(text).contains("FAIL API health");
            assertThat(text).contains("fix:");
            assertThat(text).contains("bootRun");
        } finally {
            System.setOut(old);
        }
    }

    @Test
    void doctorFailsOnInvalidConfigWithFieldHint() throws Exception {
        Files.writeString(Path.of("backline.yml"), "project: demo\nenvironment: local\nchecks: []\n");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream old = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            int code = new CommandLine(new Backline()).execute("--api-url", "http://127.0.0.1:1", "doctor");
            assertThat(code).isEqualTo(1);
            String text = out.toString(StandardCharsets.UTF_8);
            assertThat(text).contains("FAIL backline.yml");
            assertThat(text).contains("checks");
            assertThat(text).contains("fix:");
        } finally {
            System.setOut(old);
            Files.deleteIfExists(Path.of("backline.yml"));
        }
    }

    @Test
    void doctorSucceedsAgainstHealthyApi() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/health", DoctorCommandTest::handleHealth);
        server.setExecutor(null);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintStream old = System.out;
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            try {
                int code = new CommandLine(new Backline()).execute("--api-url", base, "doctor");
                assertThat(code).isZero();
                String text = out.toString(StandardCharsets.UTF_8);
                assertThat(text).contains("OK API health");
                assertThat(text).contains("OK BACKLINE_API_URL");
            } finally {
                System.setOut(old);
            }
        } finally {
            server.stop(0);
        }
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        byte[] body = "{\"data\":{\"status\":\"UP\"}}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
