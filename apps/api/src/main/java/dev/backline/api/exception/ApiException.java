package dev.backline.api.exception;

import dev.backline.core.error.ErrorCode;

/**
 * Base API failure carrying a stable {@link ErrorCode} and optional field binding for structured errors.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final String field;

    public ApiException(ErrorCode code, String message, String field) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getField() {
        return field;
    }
}
