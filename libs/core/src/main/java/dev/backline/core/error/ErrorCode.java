package dev.backline.core.error;

/**
 * Stable machine-readable codes for API and CLI structured errors.
 */
public enum ErrorCode {
    VALIDATION_ERROR,
    NOT_FOUND,
    CONFLICT,
    INTERNAL_ERROR,
    BAD_REQUEST,
    CONFIG_ERROR,
    API_UNREACHABLE;

    private final String code;

    ErrorCode() {
        this.code = name();
    }

    public String code() {
        return code;
    }
}
