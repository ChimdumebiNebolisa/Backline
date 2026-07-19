package dev.backline.cli.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JunitPolicyReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writeCreatesPassingSuiteWithoutFailureElement() throws Exception {
        Path out = tempDir.resolve("reports/policy-pass.xml");
        PolicyEvaluation evaluation = new PolicyEvaluation(true, 0, 0, 0L, List.of());

        JunitPolicyReportWriter.write(out, "abc", evaluation);

        String xml = Files.readString(out);
        assertThat(xml).contains("tests=\"1\"");
        assertThat(xml).contains("failures=\"0\"");
        assertThat(xml).contains("name=\"run-abc\"");
        assertThat(xml).doesNotContain("<failure");
    }

    @Test
    void writeEscapesXmlAndEmitsFailureForViolations() throws Exception {
        Path out = tempDir.resolve("policy-fail.xml");
        PolicyEvaluation evaluation = new PolicyEvaluation(
                false,
                1,
                0,
                0L,
                List.of("status changed & broken", "latency < expected"));

        JunitPolicyReportWriter.write(out, "<id>", evaluation);

        String xml = Files.readString(out);
        assertThat(xml).contains("failures=\"2\"");
        assertThat(xml).contains("name=\"run-&lt;id&gt;\"");
        assertThat(xml).contains("<failure message=\"Policy violation\">");
        assertThat(xml).contains("status changed &amp; broken; latency &lt; expected");
        assertThat(xml).doesNotContain("run-<id>");
    }
}
