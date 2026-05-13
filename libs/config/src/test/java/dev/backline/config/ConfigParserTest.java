package dev.backline.config;

import dev.backline.config.model.BacklineConfig;
import dev.backline.core.check.HttpMethod;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigParserTest {

    static final String CANONICAL =
            """
            project: sample-api
            environment: local

            checks:
              - key: health
                name: Health check
                method: GET
                url: http://localhost:8081/health
                expected_status: 200
                max_latency_ms: 300

              - key: get-user
                name: Fetch user
                method: GET
                url: http://localhost:8081/users/1
                expected_status: 200
                max_latency_ms: 500
                assertions:
                  - path: $.id
                    equals: 1
                  - path: $.email
                    exists: true

              - key: broken-endpoint
                name: Broken endpoint
                method: GET
                url: http://localhost:8081/broken
                expected_status: 200
                max_latency_ms: 500
            """;

    @Test
    void parsesCanonicalSampleFromStream() {
        ConfigParser parser = new ConfigParser();
        BacklineConfig cfg = parser.parse(new ByteArrayInputStream(CANONICAL.getBytes(StandardCharsets.UTF_8)), "test");
        assertThat(cfg.project()).isEqualTo("sample-api");
        assertThat(cfg.environment()).isEqualTo("local");
        assertThat(cfg.checks()).hasSize(3);
        assertThat(cfg.checks().getFirst().key()).isEqualTo("health");
        assertThat(cfg.checks().getFirst().method()).isEqualTo(HttpMethod.GET);
        assertThat(cfg.checks().get(1).assertions()).hasSize(2);
    }

    @Test
    void parsesCanonicalSampleFromTempFile() throws Exception {
        Path f = Files.createTempFile("backline", ".yml");
        Files.writeString(f, CANONICAL);
        ConfigParser parser = new ConfigParser();
        BacklineConfig cfg = parser.parse(f);
        assertThat(cfg.checks().get(2).key()).isEqualTo("broken-endpoint");
    }

    @Test
    void rejectsInvalidHttpMethod() {
        String yaml =
                """
                project: p
                environment: e
                checks:
                  - key: a
                    name: A
                    method: TRACE
                    url: http://localhost/x
                    expected_status: 200
                """;
        ConfigParser parser = new ConfigParser();
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "x"))
                .isInstanceOf(ConfigParseException.class);
    }
}
