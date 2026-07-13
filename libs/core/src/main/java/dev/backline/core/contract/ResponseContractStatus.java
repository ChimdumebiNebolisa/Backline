package dev.backline.core.contract;

/**
 * Outcome of attempting to capture an observed JSON response contract for one check result.
 */
public enum ResponseContractStatus {
    /** Full or truncated structural snapshot was produced from valid JSON. */
    CAPTURED,
    /** Response was not treated as JSON (media type / empty body). */
    NOT_JSON,
    /** Body claimed or attempted as JSON but did not parse. */
    INVALID_JSON,
    /**
     * Capture produced a usable partial contract after hitting a bound. Prefer persisting
     * {@link #CAPTURED} with {@code truncated=true}; this value remains for explicit truncated-only rows.
     */
    TRUNCATED,
    /** Per-check contract capture was disabled in config. */
    DISABLED,
    /** Capture failed unexpectedly without an HTTP transport failure. */
    ERROR
}
