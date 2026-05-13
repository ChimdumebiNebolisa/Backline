package dev.backline.core.api;

import dev.backline.core.error.ErrorCode;

/**
 * Top-level error envelope for failed API responses.
 */
public record ErrorResponse(ApiError error) {
    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(new ApiError(code.code(), message, null));
    }

    public static ErrorResponse of(ErrorCode code, String message, String field) {
        return new ErrorResponse(new ApiError(code.code(), message, field));
    }
}
