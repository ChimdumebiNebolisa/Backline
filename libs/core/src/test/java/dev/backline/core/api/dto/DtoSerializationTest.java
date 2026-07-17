package dev.backline.core.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.check.HttpMethod;
import dev.backline.core.contract.ContractSettingsDto;
import dev.backline.core.contract.ResponseContractStatus;
import dev.backline.core.run.RunEventType;
import dev.backline.core.run.RunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createRequestsRoundTripThroughJson() throws Exception {
        CreateProjectRequest project = new CreateProjectRequest("demo", "Demo");
        CreateRunRequest run =
                new CreateRunRequest("demo", "local", "hash-1", "idem-1", "cli");

        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(project), CreateProjectRequest.class))
                .isEqualTo(project);
        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(run), CreateRunRequest.class))
                .isEqualTo(run);
    }

    @Test
    void checkDefinitionAndSyncRequestRoundTrip() throws Exception {
        CheckDefinitionDto definition = new CheckDefinitionDto(
                "health",
                "Health",
                HttpMethod.GET,
                "http://localhost:8081/health",
                200,
                250,
                List.of(new AssertionDto("$.status", "UP", null)),
                ContractSettingsDto.defaults());
        CheckSyncRequest sync = new CheckSyncRequest("demo", "Demo", List.of(definition));

        String json = objectMapper.writeValueAsString(sync);
        CheckSyncRequest parsed = objectMapper.readValue(json, CheckSyncRequest.class);

        assertThat(parsed.projectSlug()).isEqualTo("demo");
        assertThat(parsed.checks()).hasSize(1);
        assertThat(parsed.checks().getFirst().method()).isEqualTo(HttpMethod.GET);
        assertThat(parsed.checks().getFirst().contract().isEnabled()).isTrue();
    }

    @Test
    void checkDefinitionLegacyConstructorOmitsContract() {
        CheckDefinitionDto definition = new CheckDefinitionDto(
                "health",
                "Health",
                HttpMethod.POST,
                "http://localhost:8081/echo",
                200,
                null,
                List.of());

        assertThat(definition.contract()).isNull();
        assertThat(definition.method()).isEqualTo(HttpMethod.POST);
    }

    @Test
    void runAndProjectProjectionsExposeFields() {
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        RunDto run = new RunDto(
                "run-1",
                "proj-1",
                "local",
                RunStatus.PASSED,
                "hash",
                "cli",
                "idem",
                now,
                now,
                now,
                1);
        ProjectDto project = new ProjectDto("proj-1", "demo", "Demo", now, now);
        ProjectSummaryDto summary = new ProjectSummaryDto(project, 3, 2, 1, 0, run);

        assertThat(run.status()).isEqualTo(RunStatus.PASSED);
        assertThat(summary.passedRuns()).isEqualTo(2);
        assertThat(summary.lastRun().id()).isEqualTo("run-1");
    }

    @Test
    void checkDtoLegacyConstructorOmitsContract() {
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        CheckDto check = new CheckDto(
                "chk-1",
                "proj-1",
                "health",
                "Health",
                HttpMethod.GET,
                "http://localhost:8081/health",
                200,
                100,
                List.of(),
                "hash",
                true,
                now,
                now);

        assertThat(check.contract()).isNull();
        assertThat(check.active()).isTrue();
    }

    @Test
    void checkResultAndDiffEntriesExposeContractFields() {
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        AssertionResultDto assertion =
                new AssertionResultDto("$.status", "UP", null, "UP", true, null);
        CheckResultDto legacy = new CheckResultDto(
                "res-1",
                "run-1",
                "chk-1",
                "health",
                "Health",
                CheckResultStatus.PASSED,
                200,
                12L,
                null,
                null,
                "{\"status\":\"UP\"}",
                List.of(assertion),
                now);
        CheckResultDto withContract = new CheckResultDto(
                "res-2",
                "run-1",
                "chk-1",
                "health",
                "Health",
                CheckResultStatus.PASSED,
                200,
                12L,
                null,
                null,
                "{\"status\":\"UP\"}",
                List.of(assertion),
                now,
                ResponseContractStatus.CAPTURED,
                "abc",
                java.util.Map.of("paths", List.of()));

        RunDiffEntry legacyEntry = new RunDiffEntry(
                "health",
                "Health",
                RunDiffChangeType.STILL_PASSING,
                CheckResultStatus.PASSED,
                CheckResultStatus.PASSED,
                200,
                200,
                10L,
                12L);
        RunDiffDto diff = new RunDiffDto("run-2", "run-1", List.of(legacyEntry));

        assertThat(legacy.responseContractStatus()).isNull();
        assertThat(withContract.responseContractHash()).isEqualTo("abc");
        assertThat(legacyEntry.contractChange()).isNull();
        assertThat(diff.previousRunId()).isEqualTo("run-1");
    }

    @Test
    void historyAndEventProjectionsExposeEnums() {
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        CheckHistoryEntry history = new CheckHistoryEntry(
                "run-1", RunStatus.FAILED, CheckResultStatus.FAILED, 500, 40L, now);
        RunEventDto event =
                new RunEventDto("evt-1", "run-1", RunEventType.COMPLETED, "done", now);

        assertThat(history.resultStatus()).isEqualTo(CheckResultStatus.FAILED);
        assertThat(event.type()).isEqualTo(RunEventType.COMPLETED);
    }
}
