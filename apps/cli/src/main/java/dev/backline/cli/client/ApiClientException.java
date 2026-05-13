package dev.backline.cli.client;

import dev.backline.core.error.ErrorCode;

/**
 * Structured failure from the Backline HTTP API.
 */
public class ApiClientException extends RuntimeException {

    private final int httpStatus;
    private final ErrorCode code;
    private final String field;

    public ApiClientException(int httpStatus, ErrorCode code, String message, String field) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.field = field;
    }

    public ApiClientException(int httpStatus, ErrorCode code, String message, String field, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.code = code;
        this.field = field;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public ErrorCode code() {
        return code;
    }

    public String field() {
        return field;
    }
}
