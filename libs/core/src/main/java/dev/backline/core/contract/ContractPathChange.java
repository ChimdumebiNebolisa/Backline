package dev.backline.core.contract;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One path-level structural change between baseline and current observed contracts.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractPathChange(
        String path,
        ContractChangeKind kind,
        List<String> previousTypes,
        List<String> currentTypes) {

    public ContractPathChange {
        previousTypes = previousTypes == null ? List.of() : List.copyOf(previousTypes);
        currentTypes = currentTypes == null ? List.of() : List.copyOf(currentTypes);
    }
}
