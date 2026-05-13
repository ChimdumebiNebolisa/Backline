package dev.backline.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BacklineCliSmokeTest {

    @Test
    void rootHelpListsSubcommands() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream old = System.out;
        System.setOut(new PrintStream(out));
        try {
            int code = new CommandLine(new Backline()).execute("--help");
            assertThat(code).isZero();
            String text = out.toString();
            assertThat(text).contains("run");
            assertThat(text).contains("doctor");
            assertThat(text).contains("init");
            assertThat(text).contains("sample");
            assertThat(text).contains("history");
            assertThat(text).contains("report");
            assertThat(text).contains("worker");
        } finally {
            System.setOut(old);
        }
    }

    @Test
    void eachDeclaredSubcommandHelpExitsZero() {
        CommandLine root = new CommandLine(new Backline());
        Set<String> subs = root.getSubcommands().keySet();
        assertThat(subs).isNotEmpty();
        for (String name : subs) {
            int code = root.execute(name, "--help");
            assertThat(code).as("help for %s", name).isZero();
        }
    }

    @Test
    void runHelpShowsNoWait() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream old = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            int code = new CommandLine(new Backline()).execute("run", "--help");
            assertThat(code).isZero();
            assertThat(out.toString(StandardCharsets.UTF_8)).contains("--no-wait");
        } finally {
            System.setOut(old);
        }
    }
}
