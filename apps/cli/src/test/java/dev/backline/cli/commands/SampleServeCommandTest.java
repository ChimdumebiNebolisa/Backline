package dev.backline.cli.commands;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SampleServeCommandTest {

    @Test
    void buildCommandUsesJavaJarInvocation() {
        var jar = Path.of("/tmp/sample-api.jar");
        var cmd = SampleServeCommand.buildCommand(jar);
        assertThat(cmd).containsExactly("java", "-jar", jar.toString());
    }
}
