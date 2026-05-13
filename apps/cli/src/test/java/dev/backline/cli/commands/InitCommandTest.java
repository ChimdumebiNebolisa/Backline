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

class InitCommandTest {

    @BeforeEach
    void clean() throws Exception {
        TestDirs.wipeDefaultWorkDir();
    }

    @Test
    void createsBacklineYmlFromTemplate() throws Exception {
        int code = new CommandLine(new Backline()).execute("init");
        assertThat(code).isZero();
        Path yml = Path.of("backline.yml").toAbsolutePath().normalize();
        assertThat(Files.exists(yml)).isTrue();
        String expected;
        try (var in = InitCommand.class.getResourceAsStream("/templates/backline.sample.yml")) {
            assertThat(in).isNotNull();
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(Files.readString(yml).replace("\r\n", "\n"))
                .isEqualTo(expected.replace("\r\n", "\n"));
        int second = new CommandLine(new Backline()).execute("init");
        assertThat(second).isNotZero();
        int third = new CommandLine(new Backline()).execute("init", "--force");
        assertThat(third).isZero();
    }
}
