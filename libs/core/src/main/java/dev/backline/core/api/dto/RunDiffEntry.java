package dev.backline.core.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.contract.ContractChangeDetail;

/**
 * One row in a run-vs-run diff describing how a check's outcome changed.
 *
 * <p>{@code contractChange} carries structured observed-response drift details even when another
 * primary {@code changeType} wins precedence.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunDiffEntry(
        String checkKey,
        String checkName,
        RunDiffChangeType changeType,
        CheckResultStatus previousStatus,
        CheckResultStatus currentStatus,
        Integer previousActualStatus,
        Integer currentActualStatus,
        Long previousLatencyMs,
        Long currentLatencyMs,
        ContractChangeDetail contractChange) {

    public RunDiffEntry(
            String checkKey,
            String checkName,
            RunDiffChangeType changeType,
            CheckResultStatus previousStatus,
            CheckResultStatus currentStatus,
            Integer previousActualStatus,
            Integer currentActualStatus,
            Long previousLatencyMs,
            Long currentLatencyMs) {
        this(
                checkKey,
                checkName,
                changeType,
                previousStatus,
                currentStatus,
                previousActualStatus,
                currentActualStatus,
                previousLatencyMs,
                currentLatencyMs,
                null);
    }
}
