package dev.backline.cli.output;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutputPrinterTest {

    @Test
    void printlnAndErrWriteToConfiguredStreams() {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        OutputPrinter printer = new OutputPrinter(
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));

        printer.println("hello");
        printer.err("boom");

        assertThat(outBuf.toString(StandardCharsets.UTF_8)).isEqualTo("hello" + System.lineSeparator());
        assertThat(errBuf.toString(StandardCharsets.UTF_8)).isEqualTo("boom" + System.lineSeparator());
    }

    @Test
    void printTableJoinsHeadersAndRowsWithPipeSeparator() {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        OutputPrinter printer = new OutputPrinter(
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        printer.printTable(
                List.of("id", "status"),
                List.of(List.of("r1", "PASSED"), Arrays.asList("r2", null)));

        String stdout = outBuf.toString(StandardCharsets.UTF_8);
        assertThat(stdout).contains("id | status");
        assertThat(stdout).contains("r1 | PASSED");
        assertThat(stdout).contains("r2 | ");
    }
}
