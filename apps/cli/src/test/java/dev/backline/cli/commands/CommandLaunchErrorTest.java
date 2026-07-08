package dev.backline.cli.commands;

import dev.backline.cli.Backline;
import dev.backline.cli.TestDirs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommandLaunchErrorTest {

    @BeforeEach
    void clean() throws Exception {
        TestDirs.wipeDefaultWorkDir();
    }

    @Test
    void historyWithInvalidConfigFailsCleanly() throws Exception {
        Files.writeString(Path.of("backline.yml"), "project: [broken");

        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            int code = new CommandLine(new Backline()).execute("history");
            assertThat(code).isEqualTo(2);
            assertThat(err.toString(StandardCharsets.UTF_8)).contains("invalid YAML");
        } finally {
            System.setErr(oldErr);
        }
    }

    @Test
    void workerWithoutJarFailsActionably() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            int code = withWorkingDir(Files.createTempDirectory("backline-worker-missing-jar"), () -> new WorkerCommand().call());
            assertThat(code).isEqualTo(1);
            assertThat(err.toString(StandardCharsets.UTF_8)).contains("Worker JAR not found");
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            System.setErr(oldErr);
        }
    }

    @Test
    void sampleServeWithoutJarFailsActionably() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            int code = withWorkingDir(Files.createTempDirectory("backline-sample-missing-jar"), () -> new SampleServeCommand().call());
            assertThat(code).isEqualTo(1);
            assertThat(err.toString(StandardCharsets.UTF_8)).contains("Sample API JAR not found");
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            System.setErr(oldErr);
        }
    }

    private static int withWorkingDir(Path directory, ThrowingCallable callable) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", directory.toAbsolutePath().toString());
        try {
            return callable.call();
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        int call() throws Exception;
    }
}
