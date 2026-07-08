package dev.backline.reporting;

import dev.backline.core.api.dto.ProjectDto;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.api.dto.RunDto;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.run.RunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultJsonReportGeneratorTest {

    @Test
    void emitsRiskScoreAndCoreSections() {
        RunDto run = new RunDto("r1", "p1", "local", RunStatus.FAILED, "h", "cli", null, Instant.now(), Instant.now(), Instant.now(), 1);
        ProjectDto project = new ProjectDto("p1", "demo", "Demo", Instant.now(), Instant.now());
        ReportInputs inputs = new ReportInputs(
                run,
                project,
                List.of(new dev.backline.core.api.dto.CheckResultDto(
                        "id", "r1", "c1", "k", "K", CheckResultStatus.FAILED, 500, 10L, "E", "m", null, List.of(), Instant.now())),
                new RunDiffDto("r1", null, List.of()),
                null,
                Instant.now());
        String json = new DefaultJsonReportGenerator().generate(inputs);
        assertThat(json).contains("\"riskScore\"").contains("\"run\"").contains("\"results\"");
    }
}
