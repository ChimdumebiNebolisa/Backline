package dev.backline.api.exception;

import dev.backline.core.error.ErrorCode;

public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    public NotFoundException(String message, String field) {
        super(ErrorCode.NOT_FOUND, message, field);
    }
}
