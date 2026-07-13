package dev.backline.core.contract;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Structured observed-contract drift for one check in a run diff.
 *
 * <p>Attached to {@link dev.backline.core.api.dto.RunDiffEntry} even when another primary
 * {@code changeType} wins precedence.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractChangeDetail(
        ContractChangeClassification classification,
        String previousHash,
        String currentHash,
        List<ContractPathChange> changes,
        boolean truncated) {

    public ContractChangeDetail {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
