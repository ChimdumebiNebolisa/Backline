package dev.backline.worker.persistence;

import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.contract.ResponseContractStatus;

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
        String assertionsJson,
        String responseContractJson,
        String responseContractHash,
        ResponseContractStatus responseContractStatus) {

    public CheckResultRow(
            UUID checkId,
            String checkKey,
            String checkName,
            CheckResultStatus status,
            Integer actualStatus,
            Long latencyMs,
            String errorCode,
            String errorMessage,
            String responsePreview,
            String assertionsJson) {
        this(
                checkId,
                checkKey,
                checkName,
                status,
                actualStatus,
                latencyMs,
                errorCode,
                errorMessage,
                responsePreview,
                assertionsJson,
                null,
                null,
                null);
    }
}
