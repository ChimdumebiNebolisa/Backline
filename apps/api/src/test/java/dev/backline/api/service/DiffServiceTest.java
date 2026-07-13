package dev.backline.api.service;

import dev.backline.api.persistence.entity.CheckEntity;
import dev.backline.api.persistence.entity.CheckResultEntity;
import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.repository.CheckRepository;
import dev.backline.api.persistence.repository.CheckResultRepository;
import dev.backline.api.persistence.repository.ProjectRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.api.support.PostgresTestBase;
import dev.backline.core.api.dto.DiffBaselineStrategy;
import dev.backline.core.api.dto.RunDiffChangeType;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.check.HttpMethod;
import dev.backline.core.run.RunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class DiffServiceTest extends PostgresTestBase {

    @Autowired
    private DiffService diffService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private CheckRepository checkRepository;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private CheckResultRepository checkResultRepository;

    @Test
    void notFound() {
        assertThatThrownBy(() -> diffService.computeDiff(UUID.randomUUID()))
                .isInstanceOf(dev.backline.api.exception.NotFoundException.class);
    }

    @Test
    void noPrevious_allNewlyFromStatus() {
        RunCtx ctx = baseProjectAndCheck("np-" + UUID.randomUUID().toString().substring(0, 8));
        RunEntity cur = saveRun(ctx.project, RunStatus.QUEUED, "2021-01-02T00:00:00Z");
        saveResult(cur, ctx.check, "a", "A", CheckResultStatus.FAILED, 500, 100L, null);
        saveResult(cur, ctx.check, "b", "B", CheckResultStatus.PASSED, 200, 10L, null);

        var diff = diffService.computeDiff(cur.getId());
        assertThat(diff.previousRunId()).isNull();
        assertThat(diff.entries().stream().collect(Collectors.toMap(e -> e.checkKey(), e -> e.changeType())))
                .containsEntry("a", RunDiffChangeType.NEWLY_FAILING)
                .containsEntry("b", RunDiffChangeType.NEWLY_PASSING);
    }

    @Test
    void withPrevious_newlyPassing_stillFailing_statusLatencyAddedRemoved() {
        RunCtx ctx = baseProjectAndCheck("wp-" + UUID.randomUUID().toString().substring(0, 8));
        RunEntity prev = saveRun(ctx.project, RunStatus.PASSED, "2021-01-01T00:00:00Z");
        prev.setFinishedAt(Instant.parse("2021-01-01T01:00:00Z"));
        runRepository.save(prev);
        saveResult(prev, ctx.check, "a", "A", CheckResultStatus.FAILED, 500, 100L, null);
        saveResult(prev, ctx.check, "b", "B", CheckResultStatus.PASSED, 200, 100L, null);
        saveResult(prev, ctx.check, "c", "C", CheckResultStatus.PASSED, 200, 100L, "[1]");
        saveResult(prev, ctx.check, "d", "D", CheckResultStatus.PASSED, 200, 100L, null);

        RunEntity cur = saveRun(ctx.project, RunStatus.QUEUED, "2021-02-01T00:00:00Z");
        saveResult(cur, ctx.check, "a", "A", CheckResultStatus.PASSED, 200, 120L, null);
        saveResult(cur, ctx.check, "b", "B", CheckResultStatus.FAILED, 500, 100L, null);
        saveResult(cur, ctx.check, "c", "C", CheckResultStatus.PASSED, 201, 100L, "[1]");
        saveResult(cur, ctx.check, "e", "E", CheckResultStatus.PASSED, 200, 50L, null);

        var diff = diffService.computeDiff(cur.getId());
        assertThat(diff.previousRunId()).isEqualTo(prev.getId().toString());
        var byKey = diff.entries().stream().collect(Collectors.toMap(e -> e.checkKey(), e -> e));
        assertThat(byKey.get("a").changeType()).isEqualTo(RunDiffChangeType.NEWLY_PASSING);
        assertThat(byKey.get("b").changeType()).isEqualTo(RunDiffChangeType.NEWLY_FAILING);
        assertThat(byKey.get("c").changeType()).isEqualTo(RunDiffChangeType.STATUS_CODE_CHANGED);
        assertThat(byKey.get("d").changeType()).isEqualTo(RunDiffChangeType.REMOVED);
        assertThat(byKey.get("e").changeType()).isEqualTo(RunDiffChangeType.NEWLY_ADDED);
    }

    @Test
    void latencyChanged_and_assertionChanged_and_stillPassing() {
        RunCtx ctx = baseProjectAndCheck("lat-" + UUID.randomUUID().toString().substring(0, 8));
        RunEntity prev = saveRun(ctx.project, RunStatus.PASSED, "2022-01-01T00:00:00Z");
        prev.setFinishedAt(Instant.parse("2022-01-01T01:00:00Z"));
        runRepository.save(prev);
        saveResult(prev, ctx.check, "x", "X", CheckResultStatus.PASSED, 200, 100L, "{\"k\":1}");
        saveResult(prev, ctx.check, "y", "Y", CheckResultStatus.PASSED, 200, 100L, "{\"k\":1}");

        RunEntity cur = saveRun(ctx.project, RunStatus.QUEUED, "2022-02-01T00:00:00Z");
        saveResult(cur, ctx.check, "x", "X", CheckResultStatus.PASSED, 200, 250L, "{\"k\":1}");
        saveResult(cur, ctx.check, "y", "Y", CheckResultStatus.PASSED, 200, 105L, "{\"k\":2}");

        var diff = diffService.computeDiff(cur.getId());
        var byKey = diff.entries().stream().collect(Collectors.toMap(e -> e.checkKey(), e -> e));
        assertThat(byKey.get("x").changeType()).isEqualTo(RunDiffChangeType.LATENCY_CHANGED);
        assertThat(byKey.get("y").changeType()).isEqualTo(RunDiffChangeType.ASSERTION_CHANGED);
    }

    @Test
    void lastPassedBaselineSkipsLatestFailedRun() {
        RunCtx ctx = baseProjectAndCheck("lp-" + UUID.randomUUID().toString().substring(0, 8));
        RunEntity passed = saveRun(ctx.project, RunStatus.PASSED, "2022-01-01T00:00:00Z");
        passed.setFinishedAt(Instant.parse("2022-01-01T00:10:00Z"));
        runRepository.save(passed);
        saveResult(passed, ctx.check, "x", "X", CheckResultStatus.PASSED, 200, 10L, null);

        RunEntity failed = saveRun(ctx.project, RunStatus.FAILED, "2022-02-01T00:00:00Z");
        failed.setFinishedAt(Instant.parse("2022-02-01T00:10:00Z"));
        runRepository.save(failed);
        saveResult(failed, ctx.check, "x", "X", CheckResultStatus.FAILED, 500, 20L, null);

        RunEntity current = saveRun(ctx.project, RunStatus.QUEUED, "2022-03-01T00:00:00Z");
        saveResult(current, ctx.check, "x", "X", CheckResultStatus.PASSED, 200, 10L, null);

        var diff = diffService.computeDiff(current.getId(), DiffBaselineStrategy.LAST_PASSED, null);
        assertThat(diff.previousRunId()).isEqualTo(passed.getId().toString());
    }

    @Test
    void fixedRunBaselineUsesRequestedRun() {
        RunCtx ctx = baseProjectAndCheck("fx-" + UUID.randomUUID().toString().substring(0, 8));
        RunEntity older = saveRun(ctx.project, RunStatus.PASSED, "2022-01-01T00:00:00Z");
        older.setFinishedAt(Instant.parse("2022-01-01T00:10:00Z"));
        runRepository.save(older);
        saveResult(older, ctx.check, "x", "X", CheckResultStatus.PASSED, 200, 10L, null);

        RunEntity newer = saveRun(ctx.project, RunStatus.FAILED, "2022-02-01T00:00:00Z");
        newer.setFinishedAt(Instant.parse("2022-02-01T00:10:00Z"));
        runRepository.save(newer);
        saveResult(newer, ctx.check, "x", "X", CheckResultStatus.FAILED, 500, 20L, null);

        RunEntity current = saveRun(ctx.project, RunStatus.QUEUED, "2022-03-01T00:00:00Z");
        saveResult(current, ctx.check, "x", "X", CheckResultStatus.PASSED, 200, 10L, null);

        var diff = diffService.computeDiff(current.getId(), DiffBaselineStrategy.FIXED_RUN, newer.getId());
        assertThat(diff.previousRunId()).isEqualTo(newer.getId().toString());
    }

    private RunCtx baseProjectAndCheck(String slug) {
        ProjectEntity p = new ProjectEntity();
        p.setSlug(slug);
        p.setName("N");
        p = projectRepository.save(p);

        CheckEntity c = new CheckEntity();
        c.setProjectId(p.getId());
        c.setKey("k1");
        c.setName("K1");
        c.setMethod(HttpMethod.GET);
        c.setUrl("http://localhost:8081/health");
        c.setExpectedStatus(200);
        c.setConfigHash("h");
        c.setActive(true);
        c = checkRepository.save(c);
        return new RunCtx(p, c);
    }

    private RunEntity saveRun(ProjectEntity project, RunStatus status, String queuedAt) {
        RunEntity r = new RunEntity();
        r.setProjectId(project.getId());
        r.setEnvironment("local");
        r.setStatus(status);
        r.setConfigHash("cfg");
        r.setAttemptCount(0);
        r.setQueuedAt(Instant.parse(queuedAt));
        return runRepository.save(r);
    }

    private void saveResult(
            RunEntity run,
            CheckEntity check,
            String key,
            String name,
            CheckResultStatus status,
            Integer actual,
            Long latency,
            String assertionsJson) {
        CheckResultEntity e = new CheckResultEntity();
        e.setRunId(run.getId());
        e.setCheckId(check.getId());
        e.setCheckKey(key);
        e.setCheckName(name);
        e.setStatus(status);
        e.setActualStatus(actual);
        e.setLatencyMs(latency);
        e.setAssertionsJson(assertionsJson);
        e.setCreatedAt(Instant.now());
        checkResultRepository.save(e);
    }

    private void saveResultWithContract(
            RunEntity run,
            CheckEntity check,
            String key,
            String name,
            CheckResultStatus status,
            Integer actual,
            Long latency,
            String contractJson,
            String contractHash,
            String contractStatus) {
        CheckResultEntity e = new CheckResultEntity();
        e.setRunId(run.getId());
        e.setCheckId(check.getId());
        e.setCheckKey(key);
        e.setCheckName(name);
        e.setStatus(status);
        e.setActualStatus(actual);
        e.setLatencyMs(latency);
        e.setResponseContractJson(contractJson);
        e.setResponseContractHash(contractHash);
        e.setResponseContractStatus(contractStatus);
        e.setCreatedAt(Instant.now());
        checkResultRepository.save(e);
    }

    @Test
    void contractBreaking_whenStillPassing() {
        RunCtx ctx = baseProjectAndCheck("ctr-" + UUID.randomUUID().toString().substring(0, 8));
        String prevJson =
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]},{\"path\":\"$.email\",\"types\":[\"string\"]}],\"truncated\":false}";
        String curJson =
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]}],\"truncated\":false}";
        String prevHash = dev.backline.core.contract.ResponseContractCanonicalizer.sha256Hex(prevJson);
        String curHash = dev.backline.core.contract.ResponseContractCanonicalizer.sha256Hex(curJson);

        RunEntity prev = saveRun(ctx.project, RunStatus.PASSED, "2023-01-01T00:00:00Z");
        prev.setFinishedAt(Instant.parse("2023-01-01T01:00:00Z"));
        runRepository.save(prev);
        saveResultWithContract(prev, ctx.check, "s", "S", CheckResultStatus.PASSED, 200, 10L, prevJson, prevHash, "CAPTURED");

        RunEntity cur = saveRun(ctx.project, RunStatus.QUEUED, "2023-02-01T00:00:00Z");
        saveResultWithContract(cur, ctx.check, "s", "S", CheckResultStatus.PASSED, 200, 10L, curJson, curHash, "CAPTURED");

        var diff = diffService.computeDiff(cur.getId());
        var entry = diff.entries().getFirst();
        assertThat(entry.changeType()).isEqualTo(RunDiffChangeType.CONTRACT_BREAKING);
        assertThat(entry.contractChange()).isNotNull();
        assertThat(entry.contractChange().classification().name()).isEqualTo("BREAKING");
    }

    @Test
    void stillFailing_withContractChange_keepsStatusPriorityButAttachesContract() {
        RunCtx ctx = baseProjectAndCheck("scf-" + UUID.randomUUID().toString().substring(0, 8));
        String prevJson =
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]},{\"path\":\"$.a\",\"types\":[\"number\"]}],\"truncated\":false}";
        String curJson =
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]},{\"path\":\"$.a\",\"types\":[\"number\"]},{\"path\":\"$.b\",\"types\":[\"string\"]}],\"truncated\":false}";
        String prevHash = dev.backline.core.contract.ResponseContractCanonicalizer.sha256Hex(prevJson);
        String curHash = dev.backline.core.contract.ResponseContractCanonicalizer.sha256Hex(curJson);

        RunEntity prev = saveRun(ctx.project, RunStatus.FAILED, "2023-03-01T00:00:00Z");
        prev.setFinishedAt(Instant.parse("2023-03-01T01:00:00Z"));
        runRepository.save(prev);
        saveResultWithContract(prev, ctx.check, "f", "F", CheckResultStatus.FAILED, 500, 10L, prevJson, prevHash, "CAPTURED");

        RunEntity cur = saveRun(ctx.project, RunStatus.QUEUED, "2023-04-01T00:00:00Z");
        // Status code also changes — that wins precedence over additive contract drift.
        saveResultWithContract(cur, ctx.check, "f", "F", CheckResultStatus.FAILED, 404, 10L, curJson, curHash, "CAPTURED");

        var entry = diffService.computeDiff(cur.getId()).entries().getFirst();
        assertThat(entry.changeType()).isEqualTo(RunDiffChangeType.STATUS_CODE_CHANGED);
        assertThat(entry.contractChange()).isNotNull();
        assertThat(entry.contractChange().classification().name()).isEqualTo("ADDITIVE");
    }

    private record RunCtx(ProjectEntity project, CheckEntity check) {}
}
