package dev.backline.core.contract;

/**
 * Central bounds for observed JSON response-contract capture.
 *
 * <p>These limits keep contract extraction safe for memory and storage. Hitting a bound stores a
 * truncated contract with an explicit reason rather than failing the HTTP check.
 */
public final class ContractLimits {

    public static final int MAX_RESPONSE_BYTES_INSPECTED = 65_536;
    public static final int MAX_JSON_DEPTH = 32;
    public static final int MAX_UNIQUE_PATHS = 512;
    public static final int MAX_OBJECT_KEYS_PER_LEVEL = 256;
    public static final int MAX_ARRAY_SAMPLES = 32;
    public static final int MAX_SERIALIZED_CONTRACT_BYTES = 32_768;
    public static final int MAX_IGNORE_PATHS = 32;
    public static final int MAX_IGNORE_PATH_LENGTH = 200;

    /** Stable truncation reason codes persisted with contracts. */
    public static final String REASON_BODY_SIZE = "body_size_limit";

    public static final String REASON_DEPTH = "depth_limit";
    public static final String REASON_PATH_COUNT = "path_count_limit";
    public static final String REASON_OBJECT_KEYS = "object_key_limit";
    public static final String REASON_ARRAY_SAMPLES = "array_sample_limit";
    public static final String REASON_SERIALIZED_SIZE = "serialized_size_limit";

    private ContractLimits() {}
}
