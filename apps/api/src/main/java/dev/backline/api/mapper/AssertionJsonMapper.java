package dev.backline.api.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.api.exception.ValidationFailedException;
import dev.backline.core.api.dto.AssertionDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AssertionJsonMapper {

    private AssertionJsonMapper() {}

    private static final Comparator<AssertionDto> ASSERTION_ORDER =
            Comparator.comparing((AssertionDto a) -> a.path() == null ? "" : a.path())
                    .thenComparing(a -> String.valueOf(a.equalsValue()))
                    .thenComparing(a -> a.exists() == null ? "" : a.exists().toString())
                    .thenComparing(a -> String.valueOf(a.notEquals()))
                    .thenComparing(a -> String.valueOf(a.contains()))
                    .thenComparing(a -> String.valueOf(a.regex()))
                    .thenComparing(a -> String.valueOf(a.gt()))
                    .thenComparing(a -> String.valueOf(a.gte()))
                    .thenComparing(a -> String.valueOf(a.lt()))
                    .thenComparing(a -> String.valueOf(a.lte()));

    /**
     * Serializes assertions to JSON for {@code assertions_json}. Empty or null lists are stored as SQL {@code NULL}
     * (not {@code "[]"}) so unset checks are distinguishable from explicitly empty assertion lists only if needed;
     * API responses treat both as an empty list.
     */
    public static String toJsonOrNull(ObjectMapper mapper, List<AssertionDto> assertions) {
        if (assertions == null || assertions.isEmpty()) {
            return null;
        }
        var sorted = new ArrayList<>(assertions);
        sorted.sort(ASSERTION_ORDER);
        try {
            return mapper.writeValueAsString(sorted);
        } catch (JsonProcessingException e) {
            throw new ValidationFailedException("could not serialize assertions", "assertions");
        }
    }

    public static List<AssertionDto> sortedCopy(List<AssertionDto> assertions) {
        if (assertions == null || assertions.isEmpty()) {
            return List.of();
        }
        var sorted = new ArrayList<>(assertions);
        sorted.sort(ASSERTION_ORDER);
        return sorted;
    }
}
