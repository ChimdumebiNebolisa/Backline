package dev.backline.cli.commands;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerCommandTest {

    @Test
    void buildCommandIncludesKeepAliveArg() {
        var cmd = WorkerCommand.buildCommand(Path.of("/tmp/worker.jar"));
        assertThat(cmd).contains("--spring.main.keep-alive=true");
    }
}
