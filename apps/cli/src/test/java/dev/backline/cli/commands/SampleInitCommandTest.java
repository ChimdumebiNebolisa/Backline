package dev.backline.cli.commands;

import dev.backline.cli.Backline;
import dev.backline.cli.TestDirs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SampleInitCommandTest {

    @BeforeEach
    void clean() throws Exception {
        TestDirs.wipeDefaultWorkDir();
    }

    @Test
    void createsSampleFiles() throws Exception {
        int code = new CommandLine(new Backline()).execute("sample", "init");
        assertThat(code).isZero();
        Path yml = Path.of("examples", "sample-api", "backline.yml").toAbsolutePath().normalize();
        Path readme = Path.of("examples", "sample-api", "README.md").toAbsolutePath().normalize();
        assertThat(Files.exists(yml)).isTrue();
        assertThat(Files.exists(readme)).isTrue();
        String expected;
        try (var in = SampleInitCommand.class.getResourceAsStream("/templates/backline.sample.yml")) {
            assertThat(in).isNotNull();
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(Files.readString(yml).replace("\r\n", "\n")).isEqualTo(expected.replace("\r\n", "\n"));
        int second = new CommandLine(new Backline()).execute("sample", "init");
        assertThat(second).isNotZero();
        int third = new CommandLine(new Backline()).execute("sample", "init", "--force");
        assertThat(third).isZero();
    }
}
