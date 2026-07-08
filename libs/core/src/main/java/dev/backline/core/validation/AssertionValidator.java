package dev.backline.core.validation;

import dev.backline.core.api.dto.AssertionDto;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared assertion rule validation used by API and config parsing.
 */
public final class AssertionValidator {

    private AssertionValidator() {}

    public static void validateSingleOperator(AssertionDto assertion) {
        if (assertion == null) {
            throw new IllegalArgumentException("assertion must not be null");
        }
        if (assertion.path() == null || assertion.path().isBlank()) {
            throw new IllegalArgumentException("assertion path must not be blank");
        }
        List<String> operators = configuredOperators(assertion);
        if (operators.isEmpty()) {
            throw new IllegalArgumentException("assertion must set exactly one supported operator");
        }
        if (operators.size() > 1) {
            throw new IllegalArgumentException("assertion must set only one operator, found: " + String.join(", ", operators));
        }
        if (assertion.regex() != null && assertion.regex().isBlank()) {
            throw new IllegalArgumentException("regex must not be blank");
        }
    }

    public static List<String> configuredOperators(AssertionDto assertion) {
        List<String> operators = new ArrayList<>();
        if (assertion.equalsValue() != null) {
            operators.add("equals");
        }
        if (assertion.exists() != null) {
            operators.add("exists");
        }
        if (assertion.notEquals() != null) {
            operators.add("not_equals");
        }
        if (assertion.contains() != null) {
            operators.add("contains");
        }
        if (assertion.regex() != null) {
            operators.add("regex");
        }
        if (assertion.gt() != null) {
            operators.add("gt");
        }
        if (assertion.gte() != null) {
            operators.add("gte");
        }
        if (assertion.lt() != null) {
            operators.add("lt");
        }
        if (assertion.lte() != null) {
            operators.add("lte");
        }
        return operators;
    }
}
