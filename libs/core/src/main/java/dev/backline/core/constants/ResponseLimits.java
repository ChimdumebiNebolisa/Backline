package dev.backline.core.constants;

/**
 * Hard limits for how much HTTP response material is retained for auditing.
 */
public final class ResponseLimits {

    public static final int RESPONSE_PREVIEW_MAX_BYTES = 4096;

    /**
     * Upper bound on how many response body bytes are read from the wire before the check is
     * errored out. Assertions need the full body, so anything larger cannot be evaluated
     * deterministically; buffering it entirely would let one target exhaust worker memory.
     */
    public static final int RESPONSE_BODY_MAX_BYTES = 10 * 1024 * 1024;

    private ResponseLimits() {}
}

