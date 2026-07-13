package dev.backline.core.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic serialization and SHA-256 fingerprinting for {@link ResponseContract}.
 *
 * <p>Object key order and whitespace must not affect the fingerprint. Scalar values never appear in
 * the payload.
 */
public final class ResponseContractCanonicalizer {

    private static final ObjectMapper CANONICAL = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private ResponseContractCanonicalizer() {}

    public static String toCanonicalJson(ResponseContract contract) {
        try {
            return CANONICAL.writeValueAsString(toCanonicalMap(contract));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize response contract", e);
        }
    }

    public static String fingerprint(ResponseContract contract) {
        return sha256Hex(toCanonicalJson(contract));
    }

    public static ResponseContract parse(String json) throws JsonProcessingException {
        return CANONICAL.readValue(json, ResponseContract.class);
    }

    public static ObjectMapper mapper() {
        return CANONICAL;
    }

    private static Map<String, Object> toCanonicalMap(ResponseContract contract) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", contract.version());
        root.put("root_type", contract.rootType());
        root.put(
                "paths",
                contract.paths().stream()
                        .map(p -> {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("path", p.path());
                            entry.put("types", p.types());
                            return entry;
                        })
                        .toList());
        root.put("truncated", contract.truncated());
        if (contract.truncated() && contract.truncationReason() != null) {
            root.put("truncation_reason", contract.truncationReason());
        }
        return root;
    }

    public static String sha256Hex(String canonicalJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Rebuild contract dropping ignored paths (and their descendants) before fingerprinting. */
    public static ResponseContract applyIgnorePaths(ResponseContract contract, List<String> ignorePaths) {
        if (ignorePaths == null || ignorePaths.isEmpty()) {
            return contract;
        }
        List<ContractPathEntry> filtered = contract.paths().stream()
                .filter(p -> ignorePaths.stream().noneMatch(i -> ContractPathSyntax.matchesIgnore(p.path(), i)))
                .toList();
        return new ResponseContract(
                contract.version(),
                contract.rootType(),
                filtered,
                contract.truncated(),
                contract.truncationReason());
    }
}
