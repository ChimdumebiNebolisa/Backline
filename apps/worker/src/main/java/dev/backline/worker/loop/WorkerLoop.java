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
 * <p>This worker does not open an HTTP port; operators should treat the process staying up and
 * structured logs as the primary liveness signal.
 *
 * <p>If request headers are ever threaded into {@link HttpCheckRequest}, log lines must redact
 * values for {@code Authorization}, {@code Cookie}, and {@code Set-Cookie} using {@link
 * dev.backline.core.constants.SensitiveHeaders}.
 */
public class WorkerLoop {

    private static final Logger log = LoggerFactory.getLogger(WorkerLoop.class);

    private final WorkerProperties props;
    private final WorkerRunDao dao;
    private final HttpCheckExecutor executor;
    private final ObjectMapper mapper;

    private volatile boolean stopped;
    private Thread workerThread;

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
        workerThread = new Thread(this::pollLoop, "backline-worker-loop");
        workerThread.setDaemon(true);
        workerThread.start();
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
    }

    private void pollLoop() {
        while (!stopped) {
            try {
                var claimed = dao.claimNextRun(props.getId());
                if (claimed.isEmpty()) {
                    sleepQuietly(props.getPollIntervalMs());
                    continue;
                }

                ClaimedRun run = claimed.get();
                log.info(
                        "Claimed run runId={} projectId={} environment={} attempt={}",
                        run.runId(),
                        run.projectId(),
                        run.environment(),
                        run.attemptCount());
                try {
                    processRun(run);
                } catch (Exception ex) {
                    log.error("Worker runtime error while processing run {}", run.runId(), ex);
                    handleWorkerFailure(run, ex);
                }
            } catch (Exception ex) {
                log.error("Worker loop error", ex);
                sleepQuietly(props.getPollIntervalMs());
            }
        }
    }

    private void handleWorkerFailure(ClaimedRun run, Exception ex) {
        if (run.attemptCount() >= props.getMaxAttempts()) {
            dao.finalizeRun(run.runId(), RunStatus.ERROR, "Worker error after max attempts: " + ex.getMessage());
        } else {
            log.warn("Scheduling retry for run {} after worker error", run.runId(), ex);
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

        for (CheckRow check : checks) {
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

            HttpCheckOutcome outcome = executor.execute(request);
            log.info(
                    "Check finished runId={} checkKey={} status={} latencyMs={}",
                    run.runId(),
                    check.key(),
                    outcome.status(),
                    outcome.latencyMs());

            if (outcome.status() == CheckResultStatus.ERROR) {
                anyError = true;
            } else if (outcome.status() == CheckResultStatus.FAILED) {
                anyFailed = true;
            }

            String assertionsJson = mapper.writeValueAsString(outcome.assertionResults());
            dao.writeCheckResult(
                    run.runId(),
                    new CheckResultRow(
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

        RunStatus terminal = anyError ? RunStatus.ERROR : anyFailed ? RunStatus.FAILED : RunStatus.PASSED;
        dao.finalizeRun(run.runId(), terminal);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
