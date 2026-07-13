package dev.backline.core.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Versioned canonical structural snapshot of a JSON response.
 *
 * <p>Empty arrays contribute only the array path (no {@code []} child) because no element samples
 * were observed. Empty objects contribute only the object path. Heterogeneous arrays union element
 * types under {@code path[]}.
 *
 * <p>This is observed-response shape data, not an OpenAPI schema and not proof of required fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseContract(
        int version,
        @JsonProperty("root_type") String rootType,
        List<ContractPathEntry> paths,
        boolean truncated,
        @JsonProperty("truncation_reason") String truncationReason) {

    public static final int CURRENT_VERSION = 1;

    public ResponseContract {
        Objects.requireNonNull(rootType, "rootType");
        paths = normalizePaths(paths);
        if (!truncated) {
            truncationReason = null;
        }
    }

    private static List<ContractPathEntry> normalizePaths(List<ContractPathEntry> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        return paths.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ContractPathEntry::path))
                .toList();
    }
}
