package dev.backline.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.check.HttpMethod;
import dev.backline.core.run.RunStatus;
import dev.backline.executor.HttpCheckExecutor;
import dev.backline.executor.HttpCheckOutcome;
import dev.backline.worker.config.WorkerProperties;
import dev.backline.worker.loop.WorkerLoop;
import dev.backline.worker.persistence.CheckResultRow;
import dev.backline.worker.persistence.CheckRow;
import dev.backline.worker.persistence.ClaimedRun;
import dev.backline.worker.persistence.WorkerRunDao;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerLoopUnitTest {

    @Test
    void startClaimsProcessesAndFinalizesRun() {
        WorkerProperties properties = testProperties();
        WorkerRunDao dao = mock(WorkerRunDao.class);
        HttpCheckExecutor executor = mock(HttpCheckExecutor.class);
        ObjectMapper objectMapper = new ObjectMapper();

        UUID runId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID checkId = UUID.randomUUID();
        ClaimedRun claimedRun = new ClaimedRun(runId, projectId, "local", "cfg", 1);
        CheckRow check = new CheckRow(
                checkId,
                "health",
                "Health",
                HttpMethod.GET,
                "http://localhost/health",
                200,
                null,
                List.of(),
                "cfg");

        AtomicBoolean firstClaim = new AtomicBoolean(true);
        when(dao.claimNextRun(anyString(), anyLong())).thenAnswer(invocation ->
                firstClaim.getAndSet(false) ? Optional.of(claimedRun) : Optional.empty());
        when(dao.loadChecksForProject(projectId)).thenReturn(List.of(check));
        when(dao.isRunCancelled(runId)).thenReturn(false);
        when(executor.execute(any())).thenReturn(new HttpCheckOutcome(
                CheckResultStatus.PASSED,
                200,
                15L,
                null,
                null,
                "{\"status\":\"UP\"}",
                List.of()));

        WorkerLoop loop = new WorkerLoop(properties, dao, executor, objectMapper);
        loop.start();

        verify(dao, timeout(3_000)).persistResultsAndFinalize(eq(runId), any(), eq(RunStatus.PASSED));
        loop.stop();
    }

    @Test
    void workerFailureRequeuesBeforeMaxAttempts() {
        WorkerProperties properties = testProperties();
        properties.setMaxAttempts(3);
        properties.setRetryBackoffMs(25);
        WorkerRunDao dao = mock(WorkerRunDao.class);
        HttpCheckExecutor executor = mock(HttpCheckExecutor.class);
        ObjectMapper objectMapper = new ObjectMapper();

        UUID runId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ClaimedRun claimedRun = new ClaimedRun(runId, projectId, "local", "cfg", 1);
        AtomicBoolean firstClaim = new AtomicBoolean(true);
        when(dao.claimNextRun(anyString(), anyLong())).thenAnswer(invocation ->
                firstClaim.getAndSet(false) ? Optional.of(claimedRun) : Optional.empty());
        when(dao.loadChecksForProject(projectId)).thenThrow(new IllegalStateException("boom"));

        WorkerLoop loop = new WorkerLoop(properties, dao, executor, objectMapper);
        loop.start();

        verify(dao, timeout(3_000)).requeueForRetry(runId, 25);
        verify(dao, never()).finalizeRun(eq(runId), eq(RunStatus.ERROR), anyString());
        loop.stop();
    }

    @Test
    void workerFailureFinalizesErrorWhenAttemptsExhausted() {
        WorkerProperties properties = testProperties();
        properties.setMaxAttempts(1);
        WorkerRunDao dao = mock(WorkerRunDao.class);
        HttpCheckExecutor executor = mock(HttpCheckExecutor.class);
        ObjectMapper objectMapper = new ObjectMapper();

        UUID runId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ClaimedRun claimedRun = new ClaimedRun(runId, projectId, "local", "cfg", 1);
        AtomicBoolean firstClaim = new AtomicBoolean(true);
        when(dao.claimNextRun(anyString(), anyLong())).thenAnswer(invocation ->
                firstClaim.getAndSet(false) ? Optional.of(claimedRun) : Optional.empty());
        when(dao.loadChecksForProject(projectId)).thenThrow(new IllegalStateException("boom"));

        WorkerLoop loop = new WorkerLoop(properties, dao, executor, objectMapper);
        loop.start();

        verify(dao, timeout(3_000)).finalizeRun(eq(runId), eq(RunStatus.ERROR), anyString());
        verify(dao, never()).requeueForRetry(eq(runId), anyLong());
        loop.stop();
    }

    @Test
    void cancelledRunSkipsFinalizeAfterChecks() {
        WorkerProperties properties = testProperties();
        WorkerRunDao dao = mock(WorkerRunDao.class);
        HttpCheckExecutor executor = mock(HttpCheckExecutor.class);
        ObjectMapper objectMapper = new ObjectMapper();

        UUID runId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID checkId = UUID.randomUUID();
        ClaimedRun claimedRun = new ClaimedRun(runId, projectId, "local", "cfg", 1);
        CheckRow check = new CheckRow(
                checkId,
                "health",
                "Health",
                HttpMethod.GET,
                "http://localhost/health",
                200,
                null,
                List.of(),
                "cfg");

        AtomicBoolean firstClaim = new AtomicBoolean(true);
        AtomicBoolean cancelledAfterCheck = new AtomicBoolean(false);
        when(dao.claimNextRun(anyString(), anyLong())).thenAnswer(invocation ->
                firstClaim.getAndSet(false) ? Optional.of(claimedRun) : Optional.empty());
        when(dao.loadChecksForProject(projectId)).thenReturn(List.of(check));
        when(dao.isRunCancelled(runId)).thenAnswer(invocation -> cancelledAfterCheck.get());
        when(executor.execute(any())).thenAnswer(invocation -> {
            cancelledAfterCheck.set(true);
            return new HttpCheckOutcome(
                    CheckResultStatus.PASSED,
                    200,
                    10L,
                    null,
                    null,
                    "{}",
                    List.of());
        });
        doAnswer(invocation -> null).when(dao).persistResultsAndFinalize(eq(runId), any(), eq(RunStatus.PASSED));

        WorkerLoop loop = new WorkerLoop(properties, dao, executor, objectMapper);
        loop.start();

        verify(dao, timeout(3_000).atLeastOnce()).isRunCancelled(runId);
        verify(dao, never()).persistResultsAndFinalize(eq(runId), any(), any());
        loop.stop();
    }

    private static WorkerProperties testProperties() {
        WorkerProperties properties = new WorkerProperties();
        properties.setId("worker-test");
        properties.setPollIntervalMs(5);
        properties.setRetryBackoffMs(10);
        properties.setStaleThresholdMs(60_000);
        properties.setJobTimeoutMs(60_000);
        return properties;
    }
}
