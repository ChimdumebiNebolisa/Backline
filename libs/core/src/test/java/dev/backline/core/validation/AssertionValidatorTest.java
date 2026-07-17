package dev.backline.core.validation;

import dev.backline.core.api.dto.AssertionDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssertionValidatorTest {

    @Test
    void acceptsExactlyOneOperator() {
        AssertionValidator.validateSingleOperator(new AssertionDto("$.id", 1, null));
        AssertionValidator.validateSingleOperator(
                new AssertionDto("$.id", null, true, null, null, null, null, null, null, null));
        AssertionValidator.validateSingleOperator(
                new AssertionDto("$.id", null, null, "x", null, null, null, null, null, null));
        AssertionValidator.validateSingleOperator(
                new AssertionDto("$.id", null, null, null, "sub", null, null, null, null, null));
        AssertionValidator.validateSingleOperator(
                new AssertionDto("$.id", null, null, null, null, "^ok$", null, null, null, null));
        AssertionValidator.validateSingleOperator(
                new AssertionDto("$.n", null, null, null, null, null, 1.0, null, null, null));
        AssertionValidator.validateSingleOperator(
                new AssertionDto("$.n", null, null, null, null, null, null, 1.0, null, null));
        AssertionValidator.validateSingleOperator(
                new AssertionDto("$.n", null, null, null, null, null, null, null, 9.0, null));
        AssertionValidator.validateSingleOperator(
                new AssertionDto("$.n", null, null, null, null, null, null, null, null, 9.0));
    }

    @Test
    void rejectsNullBlankPathMissingOrMultipleOperators() {
        assertThatThrownBy(() -> AssertionValidator.validateSingleOperator(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");

        assertThatThrownBy(() -> AssertionValidator.validateSingleOperator(
                        new AssertionDto(" ", 1, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");

        assertThatThrownBy(() -> AssertionValidator.validateSingleOperator(
                        new AssertionDto("$.id", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");

        assertThatThrownBy(() -> AssertionValidator.validateSingleOperator(
                        new AssertionDto("$.id", 1, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only one operator");

        assertThatThrownBy(() -> AssertionValidator.validateSingleOperator(
                        new AssertionDto("$.id", null, null, null, null, " ", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("regex must not be blank");
    }

    @Test
    void configuredOperatorsListsAllPresentKeys() {
        AssertionDto multi = new AssertionDto(
                "$.id", 1, true, "x", "y", "z", 1.0, 2.0, 3.0, 4.0);

        assertThat(AssertionValidator.configuredOperators(multi))
                .containsExactly(
                        "equals",
                        "exists",
                        "not_equals",
                        "contains",
                        "regex",
                        "gt",
                        "gte",
                        "lt",
                        "lte");
    }
}
