package dev.backline.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigHashTest {

    static final String ORDER_A =
            """
            project: p
            environment: e
            checks:
              - key: a
                name: A
                method: GET
                url: http://localhost/a
                expected_status: 200
              - key: b
                name: B
                method: GET
                url: http://localhost/b
                expected_status: 201
            """;

    static final String ORDER_B =
            """
            project: p
            environment: e
            checks:
              - key: b
                name: B
                method: GET
                url: http://localhost/b
                expected_status: 201
              - key: a
                name: A
                method: GET
                url: http://localhost/a
                expected_status: 200
            """;

    @Test
    void hashIsStableAcrossCheckReorder() throws Exception {
        ConfigParser parser = new ConfigParser();
        Path fa = Files.createTempFile("a", ".yml");
        Path fb = Files.createTempFile("b", ".yml");
        Files.writeString(fa, ORDER_A);
        Files.writeString(fb, ORDER_B);
        String ha = parser.canonicalConfigHash(parser.parse(fa));
        String hb = parser.canonicalConfigHash(parser.parse(fb));
        assertThat(ha).isEqualTo(hb);
    }

    @Test
    void sameConfigTwiceMatches() throws Exception {
        ConfigParser parser = new ConfigParser();
        var in = new java.io.ByteArrayInputStream(ORDER_A.getBytes(StandardCharsets.UTF_8));
        var cfg = parser.parse(in, "mem");
        String h1 = parser.canonicalConfigHash(cfg);
        String h2 = parser.canonicalConfigHash(cfg);
        assertThat(h1).isEqualTo(h2);
    }
}
