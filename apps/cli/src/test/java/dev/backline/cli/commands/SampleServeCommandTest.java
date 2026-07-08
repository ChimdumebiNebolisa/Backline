package dev.backline.cli.commands;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SampleServeCommandTest {

    @Test
    void buildCommandUsesJavaJarInvocation() {
        var cmd = SampleServeCommand.buildCommand(Path.of("/tmp/sample-api.jar"));
        assertThat(cmd).containsExactly("java", "-jar", "/tmp/sample-api.jar");
    }
}
