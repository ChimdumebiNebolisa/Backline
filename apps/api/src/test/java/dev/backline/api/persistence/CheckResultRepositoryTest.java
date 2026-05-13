package dev.backline.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.backline.api.persistence.entity.CheckEntity;
import dev.backline.api.persistence.entity.CheckResultEntity;
import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.repository.CheckRepository;
import dev.backline.api.persistence.repository.CheckResultRepository;
import dev.backline.api.persistence.repository.ProjectRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.check.HttpMethod;
import dev.backline.core.run.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CheckResultRepositoryTest extends PostgresTestBase {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private CheckRepository checkRepository;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private CheckResultRepository checkResultRepository;

    @Test
    void findByRunIdReturnsAllResultsForRun() {
        ProjectEntity project = persistProject();
        RunEntity run = persistRun(project.getId());
        CheckEntity check = persistCheck(project.getId(), "c1");

        checkResultRepository.saveAndFlush(result(run.getId(), check.getId(), "k1", Instant.parse("2024-01-01T00:00:00Z")));
        checkResultRepository.saveAndFlush(result(run.getId(), check.getId(), "k2", Instant.parse("2024-01-02T00:00:00Z")));

        assertThat(checkResultRepository.findByRunId(run.getId()))
                .extracting(CheckResultEntity::getCheckKey)
                .containsExactlyInAnyOrder("k1", "k2");
    }

    @Test
    void findByCheckIdOrderByCreatedAtDescPaginates() {
        ProjectEntity project = persistProject();
        RunEntity run = persistRun(project.getId());
        CheckEntity check = persistCheck(project.getId(), "hist");

        Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2024-02-01T00:00:00Z");
        Instant t3 = Instant.parse("2024-03-01T00:00:00Z");

        checkResultRepository.saveAndFlush(result(run.getId(), check.getId(), "a", t1));
        checkResultRepository.saveAndFlush(result(run.getId(), check.getId(), "b", t2));
        checkResultRepository.saveAndFlush(result(run.getId(), check.getId(), "c", t3));

        Page<CheckResultEntity> page = checkResultRepository.findByCheckIdOrderByCreatedAtDesc(check.getId(), PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(CheckResultEntity::getCheckKey).containsExactly("c", "b");

        Page<CheckResultEntity> page2 = checkResultRepository.findByCheckIdOrderByCreatedAtDesc(check.getId(), PageRequest.of(1, 2));
        assertThat(page2.getContent()).extracting(CheckResultEntity::getCheckKey).containsExactly("a");
    }

    private ProjectEntity persistProject() {
        ProjectEntity p = new ProjectEntity();
        p.setSlug("slug-" + UUID.randomUUID());
        p.setName("P");
        return projectRepository.saveAndFlush(p);
    }

    private RunEntity persistRun(UUID projectId) {
        RunEntity r = new RunEntity();
        r.setProjectId(projectId);
        r.setEnvironment("local");
        r.setStatus(RunStatus.QUEUED);
        r.setConfigHash("cfg");
        return runRepository.saveAndFlush(r);
    }

    private CheckEntity persistCheck(UUID projectId, String key) {
        CheckEntity c = new CheckEntity();
        c.setProjectId(projectId);
        c.setKey(key);
        c.setName(key);
        c.setMethod(HttpMethod.GET);
        c.setUrl("http://localhost");
        c.setExpectedStatus(200);
        c.setConfigHash("h");
        c.setActive(true);
        return checkRepository.saveAndFlush(c);
    }

    private static CheckResultEntity result(UUID runId, UUID checkId, String checkKey, Instant createdAt) {
        CheckResultEntity cr = new CheckResultEntity();
        cr.setRunId(runId);
        cr.setCheckId(checkId);
        cr.setCheckKey(checkKey);
        cr.setCheckName(checkKey);
        cr.setStatus(CheckResultStatus.PASSED);
        cr.setCreatedAt(createdAt);
        return cr;
    }
}
