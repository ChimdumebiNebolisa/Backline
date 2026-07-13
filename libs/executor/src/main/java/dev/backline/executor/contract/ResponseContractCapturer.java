package dev.backline.executor.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.core.contract.ContractLimits;
import dev.backline.core.contract.ContractPathEntry;
import dev.backline.core.contract.ContractSettingsDto;
import dev.backline.core.contract.JsonValueType;
import dev.backline.core.contract.ResponseContract;
import dev.backline.core.contract.ResponseContractCanonicalizer;
import dev.backline.core.contract.ResponseContractStatus;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds a bounded observed JSON response contract from an HTTP body.
 *
 * <p>Does not fail the surrounding HTTP check when limits are hit: callers store a truncated contract
 * with an explicit reason. Scalar values are never retained.
 */
public final class ResponseContractCapturer {

    private final ObjectMapper objectMapper;

    public ResponseContractCapturer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public ResponseContractCapturer() {
        this(new ObjectMapper());
    }

    /**
     * @param body full response body text available before preview truncation
     * @param contentType response Content-Type header value, may be null
     * @param settings per-check capture settings; null means warn-by-default enabled capture
     * @param alreadyParsedAsJson true when assertion evaluation already successfully treated the body as JSON
     */
    public CaptureOutcome capture(
            String body,
            String contentType,
            ContractSettingsDto settings,
            boolean alreadyParsedAsJson) {
        ContractSettingsDto resolved = settings == null ? ContractSettingsDto.defaults() : settings;
        if (!resolved.isEnabled()) {
            return CaptureOutcome.disabled();
        }

        String raw = body == null ? "" : body;
        if (raw.isEmpty()) {
            return CaptureOutcome.notJson();
        }

        boolean looksJson = alreadyParsedAsJson || isJsonMediaType(contentType) || looksLikeJson(raw);
        if (!looksJson) {
            return CaptureOutcome.notJson();
        }

        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        boolean truncatedForBody = false;
        String truncationReason = null;
        String inspect = raw;
        if (bytes.length > ContractLimits.MAX_RESPONSE_BYTES_INSPECTED) {
            inspect = new String(bytes, 0, ContractLimits.MAX_RESPONSE_BYTES_INSPECTED, StandardCharsets.UTF_8);
            truncatedForBody = true;
            truncationReason = ContractLimits.REASON_BODY_SIZE;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(inspect);
        } catch (JsonProcessingException ex) {
            // Truncated JSON may fail to parse; try full body only when we did not truncate for size.
            if (truncatedForBody) {
                return CaptureOutcome.error("truncated body could not be parsed as JSON");
            }
            return CaptureOutcome.invalidJson();
        }
        if (root == null || root.isMissingNode()) {
            return CaptureOutcome.invalidJson();
        }

        WalkState state = new WalkState();
        if (truncatedForBody) {
            state.markTruncated(ContractLimits.REASON_BODY_SIZE);
        }
        walk("$", root, 0, state);

        List<ContractPathEntry> paths = new ArrayList<>();
        for (Map.Entry<String, Set<JsonValueType>> e : state.pathTypes.entrySet()) {
            paths.add(ContractPathEntry.of(e.getKey(), e.getValue()));
        }

        ResponseContract contract = new ResponseContract(
                ResponseContract.CURRENT_VERSION,
                typeOf(root).wireName(),
                paths,
                state.truncated,
                state.truncationReason != null ? state.truncationReason : truncationReason);

        contract = ResponseContractCanonicalizer.applyIgnorePaths(contract, resolved.resolvedIgnorePaths());

        String canonical = ResponseContractCanonicalizer.toCanonicalJson(contract);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        if (canonicalBytes.length > ContractLimits.MAX_SERIALIZED_CONTRACT_BYTES) {
            // Drop trailing paths until under the bound; keep truncation visible.
            List<ContractPathEntry> reduced = new ArrayList<>(contract.paths());
            while (!reduced.isEmpty()
                    && ResponseContractCanonicalizer.toCanonicalJson(new ResponseContract(
                                            contract.version(),
                                            contract.rootType(),
                                            reduced,
                                            true,
                                            ContractLimits.REASON_SERIALIZED_SIZE))
                                    .getBytes(StandardCharsets.UTF_8)
                                    .length
                            > ContractLimits.MAX_SERIALIZED_CONTRACT_BYTES) {
                reduced.remove(reduced.size() - 1);
            }
            contract = new ResponseContract(
                    contract.version(),
                    contract.rootType(),
                    reduced,
                    true,
                    ContractLimits.REASON_SERIALIZED_SIZE);
            canonical = ResponseContractCanonicalizer.toCanonicalJson(contract);
        }

        String fingerprint = ResponseContractCanonicalizer.fingerprint(contract);
        ResponseContractStatus status = contract.truncated()
                ? ResponseContractStatus.CAPTURED
                : ResponseContractStatus.CAPTURED;
        // Persist TRUNCATED when truncated so status column surfaces the bound explicitly.
        if (contract.truncated()) {
            status = ResponseContractStatus.TRUNCATED;
        }
        return new CaptureOutcome(status, contract, canonical, fingerprint, null);
    }

    private void walk(String path, JsonNode node, int depth, WalkState state) {
        if (state.stop) {
            return;
        }
        if (depth > ContractLimits.MAX_JSON_DEPTH) {
            state.markTruncated(ContractLimits.REASON_DEPTH);
            state.stop = true;
            return;
        }
        if (!state.addType(path, typeOf(node))) {
            return;
        }

        if (node.isObject()) {
            int keyCount = 0;
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            // Deterministic walk order for path discovery under caps.
            Map<String, JsonNode> ordered = new TreeMap<>();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                ordered.put(field.getKey(), field.getValue());
            }
            for (Map.Entry<String, JsonNode> field : ordered.entrySet()) {
                if (state.stop) {
                    return;
                }
                keyCount++;
                if (keyCount > ContractLimits.MAX_OBJECT_KEYS_PER_LEVEL) {
                    state.markTruncated(ContractLimits.REASON_OBJECT_KEYS);
                    state.stop = true;
                    return;
                }
                walk(path + "." + field.getKey(), field.getValue(), depth + 1, state);
            }
            return;
        }

        if (node.isArray()) {
            int limit = Math.min(node.size(), ContractLimits.MAX_ARRAY_SAMPLES);
            if (node.size() > ContractLimits.MAX_ARRAY_SAMPLES) {
                state.markTruncated(ContractLimits.REASON_ARRAY_SAMPLES);
            }
            String elementPath = path + "[]";
            for (int i = 0; i < limit; i++) {
                if (state.stop) {
                    return;
                }
                walk(elementPath, node.get(i), depth + 1, state);
            }
        }
    }

    private static JsonValueType typeOf(JsonNode node) {
        if (node == null || node.isNull()) {
            return JsonValueType.NULL;
        }
        if (node.isObject()) {
            return JsonValueType.OBJECT;
        }
        if (node.isArray()) {
            return JsonValueType.ARRAY;
        }
        if (node.isBoolean()) {
            return JsonValueType.BOOLEAN;
        }
        if (node.isNumber()) {
            return JsonValueType.NUMBER;
        }
        if (node.isTextual()) {
            return JsonValueType.STRING;
        }
        // Fallback for uncommon Jackson node types (binary, missing): treat as string-ish structure.
        return JsonValueType.STRING;
    }

    static boolean isJsonMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String primary = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return primary.equals("application/json")
                || primary.equals("application/problem+json")
                || primary.endsWith("+json");
    }

    private static boolean looksLikeJson(String body) {
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == '{' || c == '[';
        }
        return false;
    }

    private static final class WalkState {
        private final Map<String, Set<JsonValueType>> pathTypes = new LinkedHashMap<>();
        private boolean truncated;
        private String truncationReason;
        private boolean stop;

        boolean addType(String path, JsonValueType type) {
            Set<JsonValueType> existing = pathTypes.get(path);
            if (existing == null && pathTypes.size() >= ContractLimits.MAX_UNIQUE_PATHS) {
                markTruncated(ContractLimits.REASON_PATH_COUNT);
                stop = true;
                return false;
            }
            pathTypes.computeIfAbsent(path, k -> new LinkedHashSet<>()).add(type);
            return true;
        }

        void markTruncated(String reason) {
            truncated = true;
            if (truncationReason == null) {
                truncationReason = reason;
            }
        }
    }

    /**
     * Result of a capture attempt. {@code contractJson} is the canonical serialization used for storage.
     */
    public record CaptureOutcome(
            ResponseContractStatus status,
            ResponseContract contract,
            String contractJson,
            String fingerprint,
            String errorMessage) {

        public static CaptureOutcome disabled() {
            return new CaptureOutcome(ResponseContractStatus.DISABLED, null, null, null, null);
        }

        public static CaptureOutcome notJson() {
            return new CaptureOutcome(ResponseContractStatus.NOT_JSON, null, null, null, null);
        }

        public static CaptureOutcome invalidJson() {
            return new CaptureOutcome(ResponseContractStatus.INVALID_JSON, null, null, null, null);
        }

        public static CaptureOutcome error(String message) {
            String bounded = message == null
                    ? "contract capture failed"
                    : message.substring(0, Math.min(message.length(), 200));
            return new CaptureOutcome(ResponseContractStatus.ERROR, null, null, null, bounded);
        }
    }
}
