package dev.backline.worker.persistence;

import java.util.UUID;

/**
 * Snapshot of a run claimed by this worker. {@code lockedBy} is the claim ownership token:
 * later finalize/requeue calls must present it so a stale worker cannot mutate a run that
 * was recovered and re-claimed by another worker in the meantime.
 */
public record ClaimedRun(
        UUID runId,
        UUID projectId,
        String environment,
        String configHash,
        int attemptCount,
        String lockedBy) {}

