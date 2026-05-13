package dev.backline.core.api;

import java.util.List;

/**
 * Wrapper for a paginated collection in successful API responses.
 */
public record ListResponse<T>(List<T> data, PageMeta page) {
    public static <T> ListResponse<T> of(List<T> data, PageMeta page) {
        return new ListResponse<>(data, page);
    }
}
