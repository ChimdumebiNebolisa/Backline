package dev.backline.core.contract;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One structural path in an observed response contract.
 *
 * <p>{@code types} is a sorted, de-duplicated set of {@link JsonValueType#wireName()} values. Scalar
 * values are never stored.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractPathEntry(String path, List<String> types) {

    public ContractPathEntry {
        Objects.requireNonNull(path, "path");
        types = normalizeTypes(types);
    }

    private static List<String> normalizeTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        return types.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public static ContractPathEntry of(String path, Iterable<JsonValueType> valueTypes) {
        List<String> names = new ArrayList<>();
        for (JsonValueType t : valueTypes) {
            names.add(t.wireName());
        }
        return new ContractPathEntry(path, names);
    }
}
