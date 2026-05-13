package dev.backline.api.exception;

import dev.backline.core.error.ErrorCode;

public class ValidationFailedException extends ApiException {

    public ValidationFailedException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }

    public ValidationFailedException(String message, String field) {
        super(ErrorCode.VALIDATION_ERROR, message, field);
    }
}
