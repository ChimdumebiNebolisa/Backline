package dev.backline.core.api;

/**
 * Wrapper for a single resource in successful API responses.
 */
public record DataResponse<T>(T data) {
    public static <T> DataResponse<T> of(T data) {
        return new DataResponse<>(data);
    }
}
