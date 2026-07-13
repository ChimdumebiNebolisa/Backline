package dev.backline.api.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.backline.api.persistence.PostgresTestBase;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class MigrationSmokeTest extends PostgresTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private CheckRepository checkRepository;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private CheckResultRepository checkResultRepository;

    @Test
    void flywayCreatesExpectedTables() {
        assertThat(tableExists("projects")).isTrue();
        assertThat(tableExists("checks")).isTrue();
        assertThat(tableExists("runs")).isTrue();
        assertThat(tableExists("check_results")).isTrue();
        assertThat(tableExists("run_events")).isTrue();
    }

    @Test
    void responseContractColumnsExistAndAcceptNullableLegacyRows() {
        assertThat(columnExists("checks", "contract_json")).isTrue();
        assertThat(columnExists("check_results", "response_contract_json")).isTrue();
        assertThat(columnExists("check_results", "response_contract_hash")).isTrue();
        assertThat(columnExists("check_results", "response_contract_status")).isTrue();

        ProjectEntity project = persistProject();
        RunEntity run = runRepository.saveAndFlush(newRun(project.getId(), "idem-contract-" + UUID.randomUUID()));
        CheckResultEntity legacy = newResult(run.getId(), "legacy");
        checkResultRepository.saveAndFlush(legacy);
        CheckResultEntity reloaded = checkResultRepository.findById(legacy.getId()).orElseThrow();
        assertThat(reloaded.getResponseContractJson()).isNull();
        assertThat(reloaded.getResponseContractHash()).isNull();
        assertThat(reloaded.getResponseContractStatus()).isNull();

        CheckResultEntity captured = newResult(run.getId(), "captured");
        captured.setResponseContractJson(
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[],\"truncated\":false}");
        captured.setResponseContractHash("abc");
        captured.setResponseContractStatus("CAPTURED");
        checkResultRepository.saveAndFlush(captured);

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        update check_results
                                        set response_contract_status = 'BOGUS'
                                        where id = ?
                                        """,
                                        captured.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = ? and column_name = ?
                """,
                Integer.class,
                table,
                column);
        return count != null && count > 0;
    }

    @Test
    void duplicateProjectScopedCheckKeyViolatesUniqueConstraint() {
        ProjectEntity project = persistProject();

        checkRepository.saveAndFlush(newCheck(project.getId(), "same-key"));
        assertThatThrownBy(() -> checkRepository.saveAndFlush(newCheck(project.getId(), "same-key")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidRunStatusViolatesCheckConstraint() {
        ProjectEntity project = persistProject();

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        insert into runs (project_id, environment, status, config_hash)
                                        values (?, 'local', 'BOGUS', 'cfg')
                                        """,
                                        project.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateIdempotencyKeyViolatesUniqueConstraint() {
        ProjectEntity project = persistProject();
        String idempotencyKey = "idem-" + UUID.randomUUID();

        runRepository.saveAndFlush(newRun(project.getId(), idempotencyKey));
        assertThatThrownBy(() -> runRepository.saveAndFlush(newRun(project.getId(), idempotencyKey)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateRunScopedCheckKeyViolatesUniqueConstraint() {
        ProjectEntity project = persistProject();
        RunEntity run = runRepository.saveAndFlush(newRun(project.getId(), null));

        checkResultRepository.saveAndFlush(newResult(run.getId(), "k1"));
        assertThatThrownBy(() -> checkResultRepository.saveAndFlush(newResult(run.getId(), "k1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidExpectedStatusViolatesCheckConstraint() {
        ProjectEntity project = persistProject();

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        insert into checks (project_id, key, name, method, url, expected_status, config_hash)
                                        values (?, 'k', 'n', 'GET', 'http://localhost', 700, 'hash')
                                        """,
                                        project.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean tableExists(String name) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)::int from information_schema.tables
                        where table_schema = 'public' and table_name = ?
                        """,
                        Integer.class,
                        name);
        return count != null && count == 1;
    }

    private ProjectEntity persistProject() {
        ProjectEntity p = new ProjectEntity();
        p.setSlug("slug-" + UUID.randomUUID());
        p.setName("Test Project");
        return projectRepository.saveAndFlush(p);
    }

    private static CheckEntity newCheck(UUID projectId, String key) {
        CheckEntity c = new CheckEntity();
        c.setProjectId(projectId);
        c.setKey(key);
        c.setName("Check " + key);
        c.setMethod(HttpMethod.GET);
        c.setUrl("http://localhost/health");
        c.setExpectedStatus(200);
        c.setConfigHash("hash");
        c.setActive(true);
        return c;
    }

    private static RunEntity newRun(UUID projectId, String idempotencyKey) {
        RunEntity r = new RunEntity();
        r.setProjectId(projectId);
        r.setEnvironment("local");
        r.setStatus(RunStatus.QUEUED);
        r.setConfigHash("cfg");
        r.setIdempotencyKey(idempotencyKey);
        return r;
    }

    private static CheckResultEntity newResult(UUID runId, String checkKey) {
        CheckResultEntity cr = new CheckResultEntity();
        cr.setRunId(runId);
        cr.setCheckKey(checkKey);
        cr.setCheckName("Check " + checkKey);
        cr.setStatus(CheckResultStatus.PASSED);
        return cr;
    }
}
