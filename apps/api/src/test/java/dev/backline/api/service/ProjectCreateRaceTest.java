package dev.backline.api.service;

import dev.backline.api.exception.ConflictException;
import dev.backline.api.support.PostgresTestBase;
import dev.backline.core.api.dto.CreateProjectRequest;
import dev.backline.core.api.dto.ProjectDto;
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
 * Regression tests for the project-create slug race (audit finding F-008): concurrent creates
 * for one slug must produce at most one success, and every loser must surface as a structured
 * 409 ConflictException instead of an unstructured 500 from the unique-index violation.
 */
class ProjectCreateRaceTest extends PostgresTestBase {

    @Autowired
    private ProjectService projectService;

    @Test
    void concurrentCreatesOfSameSlugYieldOneSuccessAndConflicts() throws Exception {
        String slug = "race-" + UUID.randomUUID().toString().substring(0, 8);
        int threads = 6;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit((Callable<Object>) () -> {
                start.await();
                try {
                    return projectService.create(new CreateProjectRequest(slug, slug));
                } catch (ConflictException e) {
                    return e;
                }
            }));
        }
        start.countDown();

        int created = 0;
        int conflicts = 0;
        List<Object> collected = new ArrayList<>();
        for (Future<Object> f : results) {
            collected.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        for (Object r : collected) {
            if (r instanceof ProjectDto) created++;
            else if (r instanceof ConflictException) conflicts++;
        }

        assertThat(created).isEqualTo(1);
        assertThat(conflicts).isEqualTo(threads - 1);
    }
}
