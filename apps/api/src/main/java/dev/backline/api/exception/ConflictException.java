package dev.backline.api.exception;

import dev.backline.core.error.ErrorCode;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public ConflictException(ErrorCode code, String message, String field) {
        super(code, message, field);
    }
}
