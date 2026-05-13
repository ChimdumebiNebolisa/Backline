package dev.backline.worker.persistence;

import dev.backline.core.check.CheckResultStatus;

import java.util.UUID;

public record CheckResultRow(
        UUID checkId,
        String checkKey,
        String checkName,
        CheckResultStatus status,
        Integer actualStatus,
        Long latencyMs,
        String errorCode,
        String errorMessage,
        String responsePreview,
        String assertionsJson) {}
