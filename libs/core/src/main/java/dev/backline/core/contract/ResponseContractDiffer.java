package dev.backline.core.contract;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Classifies structural drift between two persisted observed contracts.
 *
 * <p>Rules (summary): removed path or type set change → breaking; added path → additive; only adding
 * {@code null} to an existing type set → noisy; unavailable capture on either side → unavailable;
 * identical fingerprints → unchanged. Nullable/required cannot be proven from one response.
 */
public final class ResponseContractDiffer {

    private ResponseContractDiffer() {}

    public static ContractChangeDetail diff(
            ResponseContractStatus previousStatus,
            String previousJson,
            String previousHash,
            ResponseContractStatus currentStatus,
            String currentJson,
            String currentHash) {

        boolean eitherUnavailable = !isComparable(previousStatus, previousJson, previousHash)
                || !isComparable(currentStatus, currentJson, currentHash);
        if (eitherUnavailable) {
            return new ContractChangeDetail(
                    ContractChangeClassification.UNAVAILABLE,
                    previousHash,
                    currentHash,
                    List.of(),
                    isTruncatedStatus(previousStatus) || isTruncatedStatus(currentStatus));
        }

        if (Objects.equals(previousHash, currentHash)) {
            boolean truncated = isTruncated(previousJson) || isTruncated(currentJson);
            return new ContractChangeDetail(
                    ContractChangeClassification.UNCHANGED, previousHash, currentHash, List.of(), truncated);
        }

        ResponseContract previous = mustParse(previousJson);
        ResponseContract current = mustParse(currentJson);
        Map<String, List<String>> prevPaths = index(previous);
        Map<String, List<String>> curPaths = index(current);

        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(prevPaths.keySet());
        keys.addAll(curPaths.keySet());

        List<ContractPathChange> changes = new ArrayList<>();
        boolean anyBreaking = false;
        boolean anyAdditive = false;
        boolean anyNoisy = false;

        for (String path : keys.stream().sorted().toList()) {
            List<String> prevTypes = prevPaths.get(path);
            List<String> curTypes = curPaths.get(path);
            if (prevTypes == null) {
                changes.add(new ContractPathChange(path, ContractChangeKind.ADDED, List.of(), curTypes));
                anyAdditive = true;
                continue;
            }
            if (curTypes == null) {
                changes.add(new ContractPathChange(path, ContractChangeKind.REMOVED, prevTypes, List.of()));
                anyBreaking = true;
                continue;
            }
            if (prevTypes.equals(curTypes)) {
                continue;
            }
            if (isNullOnlyAddition(prevTypes, curTypes)) {
                changes.add(new ContractPathChange(path, ContractChangeKind.TYPE_CHANGED, prevTypes, curTypes));
                anyNoisy = true;
                continue;
            }
            changes.add(new ContractPathChange(path, ContractChangeKind.TYPE_CHANGED, prevTypes, curTypes));
            anyBreaking = true;
        }

        changes.sort(Comparator.comparing(ContractPathChange::path));

        ContractChangeClassification classification;
        if (anyBreaking) {
            classification = ContractChangeClassification.BREAKING;
        } else if (anyAdditive) {
            classification = ContractChangeClassification.ADDITIVE;
        } else if (anyNoisy) {
            classification = ContractChangeClassification.NOISY;
        } else {
            classification = ContractChangeClassification.UNCHANGED;
        }

        boolean truncated = previous.truncated() || current.truncated();
        return new ContractChangeDetail(classification, previousHash, currentHash, changes, truncated);
    }

    private static boolean isComparable(ResponseContractStatus status, String json, String hash) {
        if (status == null || status == ResponseContractStatus.DISABLED || status == ResponseContractStatus.NOT_JSON
                || status == ResponseContractStatus.INVALID_JSON || status == ResponseContractStatus.ERROR) {
            return false;
        }
        if (hash == null || hash.isBlank()) {
            return false;
        }
        if (json == null || json.isBlank()) {
            return false;
        }
        return status == ResponseContractStatus.CAPTURED || status == ResponseContractStatus.TRUNCATED;
    }

    private static boolean isTruncatedStatus(ResponseContractStatus status) {
        return status == ResponseContractStatus.TRUNCATED;
    }

    private static boolean isTruncated(String json) {
        try {
            return ResponseContractCanonicalizer.parse(json).truncated();
        } catch (Exception e) {
            return false;
        }
    }

    private static ResponseContract mustParse(String json) {
        try {
            return ResponseContractCanonicalizer.parse(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid contract json", e);
        }
    }

    private static Map<String, List<String>> index(ResponseContract contract) {
        Map<String, List<String>> map = new TreeMap<>();
        for (ContractPathEntry entry : contract.paths()) {
            map.put(entry.path(), entry.types());
        }
        return map;
    }

    /** True when current types equal previous plus {@code null} only. */
    static boolean isNullOnlyAddition(List<String> previous, List<String> current) {
        if (current.size() != previous.size() + 1) {
            return false;
        }
        if (!current.contains(JsonValueType.NULL.wireName())) {
            return false;
        }
        if (previous.contains(JsonValueType.NULL.wireName())) {
            return false;
        }
        Set<String> withoutNull = new LinkedHashSet<>(current);
        withoutNull.remove(JsonValueType.NULL.wireName());
        return withoutNull.equals(new LinkedHashSet<>(previous));
    }
}
