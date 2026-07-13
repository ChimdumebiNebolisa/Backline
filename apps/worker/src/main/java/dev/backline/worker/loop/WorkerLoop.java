package dev.backline.worker.loop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.run.RunStatus;
import dev.backline.executor.HttpCheckExecutor;
import dev.backline.executor.HttpCheckOutcome;
import dev.backline.executor.HttpCheckRequest;
import dev.backline.worker.config.WorkerProperties;
import dev.backline.worker.persistence.CheckResultRow;
import dev.backline.worker.persistence.CheckRow;
import dev.backline.worker.persistence.ClaimedRun;
import dev.backline.worker.persistence.WorkerRunDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Polls the database for queued runs, executes checks, persists results, and finalizes runs.
 *
 * <p>Periodically scans for stale RUNNING runs (worker crash recovery) and returns them to
 * QUEUED or marks them ERROR based on the retry policy.
 *
 * <p>This worker does not open an HTTP port; operators should treat the process staying up and
 * structured logs as the primary liveness signal.
 */
public class WorkerLoop {

    private static final Logger log = LoggerFactory.getLogger(WorkerLoop.class);

    private final WorkerProperties props;
    private final WorkerRunDao dao;
    private final HttpCheckExecutor executor;
    private final ObjectMapper mapper;

    private volatile boolean stopped;
    private Thread workerThread;
    private long lastStaleRecoveryMs;

    public WorkerLoop(WorkerProperties props, WorkerRunDao dao, HttpCheckExecutor executor, ObjectMapper mapper) {
        this.props = props;
        this.dao = dao;
        this.executor = executor;
        this.mapper = mapper;
    }

    public void start() {
        if (workerThread != null && workerThread.isAlive()) {
            return;
        }
        stopped = false;
        lastStaleRecoveryMs = System.currentTimeMillis();
        workerThread = new Thread(this::pollLoop, "backline-worker-loop");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("Worker started workerId={}", props.getId());
    }

    public void stop() {
        stopped = true;
        if (workerThread != null) {
            try {
                workerThread.join(10_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            workerThread = null;
        }
        log.info("Worker stopped workerId={}", props.getId());
    }

    private void pollLoop() {
        while (!stopped) {
            try {
                recoverStaleRunsIfDue();

                var claimed = dao.claimNextRun(props.getId(), props.getJobTimeoutMs());
                if (claimed.isEmpty()) {
                    sleepQuietly(props.getPollIntervalMs());
                    continue;
                }

                ClaimedRun run = claimed.get();
                log.info("run.claimed runId={} projectId={} environment={} workerId={} attempt={}",
                        run.runId(), run.projectId(), run.environment(), props.getId(), run.attemptCount());
                try {
                    processRun(run);
                } catch (Exception ex) {
                    log.error("run.error runId={} workerId={} attempt={} error={}",
                            run.runId(), props.getId(), run.attemptCount(), ex.getMessage());
                    handleWorkerFailure(run, ex);
                }
            } catch (Exception ex) {
                log.error("Worker loop error workerId={}", props.getId(), ex);
                sleepQuietly(props.getPollIntervalMs());
            }
        }
    }

    private void recoverStaleRunsIfDue() {
        long now = System.currentTimeMillis();
        long interval = Math.max(props.getStaleThresholdMs() / 2, 10_000);
        if (now - lastStaleRecoveryMs >= interval) {
            lastStaleRecoveryMs = now;
            try {
                int recovered = dao.recoverStaleRuns(
                        props.getStaleThresholdMs(), props.getMaxAttempts(), props.getRetryBackoffMs());
                if (recovered > 0) {
                    log.info("stale.recovered count={} workerId={}", recovered, props.getId());
                }
            } catch (Exception ex) {
                log.error("Stale recovery error workerId={}", props.getId(), ex);
            }
        }
    }

    private void handleWorkerFailure(ClaimedRun run, Exception ex) {
        if (run.attemptCount() >= props.getMaxAttempts()) {
            log.warn("run.maxAttemptsExhausted runId={} workerId={} attempts={}",
                    run.runId(), props.getId(), run.attemptCount());
            dao.finalizeRun(run.runId(), RunStatus.ERROR, "Worker error after max attempts: " + ex.getMessage());
        } else {
            log.info("run.retryScheduled runId={} workerId={} attempt={} backoffMs={}",
                    run.runId(), props.getId(), run.attemptCount(), props.getRetryBackoffMs());
            dao.requeueForRetry(run.runId(), props.getRetryBackoffMs());
        }
    }

    private void processRun(ClaimedRun run) throws JsonProcessingException {
        List<CheckRow> checks = dao.loadChecksForProject(run.projectId());
        if (checks.isEmpty()) {
            dao.finalizeRun(run.runId(), RunStatus.ERROR, "NO_CHECKS: no active checks for project");
            return;
        }

        boolean anyError = false;
        boolean anyFailed = false;
        List<CheckResultRow> rows = new java.util.ArrayList<>();

        for (CheckRow check : checks) {
            if (dao.isRunCancelled(run.runId())) {
                log.info("run.cancelled runId={} workerId={} stoppedBeforeCheck={}",
                        run.runId(), props.getId(), check.key());
                return;
            }

            HttpCheckRequest request = new HttpCheckRequest(
                    check.checkId() == null ? null : check.checkId().toString(),
                    check.key(),
                    check.name(),
                    check.method(),
                    check.url(),
                    check.expectedStatus(),
                    check.maxLatencyMs(),
                    check.assertions(),
                    null);

            log.info("check.started runId={} checkKey={} workerId={}", run.runId(), check.key(), props.getId());
            HttpCheckOutcome outcome = executor.execute(request);
            log.info("check.completed runId={} checkKey={} status={} latencyMs={} workerId={}",
                    run.runId(), check.key(), outcome.status(), outcome.latencyMs(), props.getId());

            if (outcome.status() == CheckResultStatus.ERROR) {
                anyError = true;
            } else if (outcome.status() == CheckResultStatus.FAILED) {
                anyFailed = true;
            }

            String assertionsJson = mapper.writeValueAsString(outcome.assertionResults());
            rows.add(new CheckResultRow(
                    check.checkId(),
                    check.key(),
                    check.name(),
                    outcome.status(),
                    outcome.actualStatus(),
                    outcome.latencyMs(),
                    outcome.errorCode(),
                    outcome.errorMessage(),
                    outcome.responsePreview(),
                    assertionsJson));
        }

        if (dao.isRunCancelled(run.runId())) {
            log.info("run.cancelled runId={} workerId={} afterAllChecks=true", run.runId(), props.getId());
            return;
        }

        RunStatus terminal = resolveTerminalStatus(anyError, anyFailed);
        dao.persistResultsAndFinalize(run.runId(), rows, terminal);
        log.info("run.completed runId={} status={} workerId={} attempt={}",
                run.runId(), terminal, props.getId(), run.attemptCount());
    }

    /**
     * Aggregates per-check outcomes into a terminal run status.
     *
     * <p>An assertion or status/latency {@code FAILED} is a real regression signal and takes precedence
     * over a transport/runtime {@code ERROR}: a run is only reported as {@code ERROR} when no check
     * failed but at least one errored. This keeps genuine regressions from being masked as infrastructure
     * problems, matching the guardrail that assertion failures and worker errors are distinct.
     */
    static RunStatus resolveTerminalStatus(boolean anyError, boolean anyFailed) {
        if (anyFailed) {
            return RunStatus.FAILED;
        }
        if (anyError) {
            return RunStatus.ERROR;
        }
        return RunStatus.PASSED;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
