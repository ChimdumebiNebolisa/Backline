package dev.backline.cli.policy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes a tiny JUnit XML report for CI parsers.
 */
public final class JunitPolicyReportWriter {

    private JunitPolicyReportWriter() {}

    public static void write(Path outputPath, String runId, PolicyEvaluation evaluation) throws IOException {
        Path absolutePath = outputPath.toAbsolutePath().normalize();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String xml = toXml(runId, evaluation);
        Files.writeString(absolutePath, xml);
    }

    private static String toXml(String runId, PolicyEvaluation evaluation) {
        List<String> violations = evaluation.violations();
        int failures = violations.isEmpty() ? 0 : violations.size();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<testsuite name=\"backline.policy\" tests=\"1\" failures=\"")
                .append(failures)
                .append("\">\n");
        sb.append("  <testcase classname=\"backline.policy\" name=\"run-")
                .append(escapeXml(runId))
                .append("\">");
        if (!evaluation.passed()) {
            sb.append("\n    <failure message=\"Policy violation\">");
            sb.append(escapeXml(String.join("; ", violations)));
            sb.append("</failure>\n  ");
        }
        sb.append("</testcase>\n</testsuite>\n");
        return sb.toString();
    }

    private static String escapeXml(String input) {
        String value = input == null ? "" : input;
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
