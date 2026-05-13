package dev.backline.worker.persistence;

import java.util.UUID;

public record ClaimedRun(UUID runId, UUID projectId, String environment, String configHash, int attemptCount) {}
