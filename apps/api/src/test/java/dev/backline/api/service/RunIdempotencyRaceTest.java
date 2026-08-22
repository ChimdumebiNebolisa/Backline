package dev.backline.api.service;

import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.repository.ProjectRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.api.support.PostgresTestBase;
import dev.backline.core.api.dto.CreateRunRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for concurrent duplicate submissions carrying one idempotency key
 * (audit finding F-002): both callers must receive the same run instead of one of them
 * failing on uq_runs_idempotency_key as an unstructured 500.
 */
class RunIdempotencyRaceTest extends PostgresTestBase {

    @Autowired
    private RunService runService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RunRepository runRepository;

    @Test
    void concurrentSubmissionsWithSameKeyAllReplayOneRun() throws Exception {
        ProjectEntity project = saveProject("idem-race-" + UUID.randomUUID().toString().substring(0, 8));
        String key = "race-" + UUID.randomUUID();
        int threads = 6;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit((Callable<String>) () -> {
                start.await();
                return runService
                        .submit(new CreateRunRequest(project.getSlug(), "local", "cfg", key, "test"))
                        .id();
            }));
        }
        start.countDown();
        List<String> ids = new ArrayList<>();
        for (Future<String> f : results) {
            ids.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(ids).hasSize(threads);
        assertThat(ids).containsOnly(ids.getFirst());
        assertThat(runRepository.findByIdempotencyKey(key)).isPresent();
        Long rowsForKey = runRepository.findAll().stream()
                .filter(r -> key.equals(r.getIdempotencyKey()))
                .count();
        assertThat(rowsForKey).isEqualTo(1L);
    }

    private ProjectEntity saveProject(String slug) {
        ProjectEntity project = new ProjectEntity();
        project.setSlug(slug);
        project.setName(slug);
        return projectRepository.save(project);
    }
}
