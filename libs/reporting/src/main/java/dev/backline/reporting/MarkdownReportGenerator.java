package dev.backline.reporting;

/**
 * Generates a Markdown report from API snapshots supplied by the CLI. Does not access the database.
 */
public interface MarkdownReportGenerator {
    String generate(ReportInputs inputs);
}
