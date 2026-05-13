package dev.backline.cli.output;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

/** Small helpers for consistent CLI output. */
public final class OutputPrinter {

    private final PrintStream out;
    private final PrintStream err;

    public OutputPrinter() {
        this(System.out, System.err);
    }

    public OutputPrinter(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public void println(String line) {
        out.println(line);
    }

    public void err(String line) {
        err.println(line);
    }

    public void printTable(List<String> headers, List<List<String>> rows) {
        println(headers.stream().map(Object::toString).collect(Collectors.joining(" | ")));
        for (List<String> row : rows) {
            println(row.stream().map(c -> c == null ? "" : c).collect(Collectors.joining(" | ")));
        }
    }
}
