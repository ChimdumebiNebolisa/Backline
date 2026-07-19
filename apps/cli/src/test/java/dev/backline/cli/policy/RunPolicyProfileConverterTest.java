package dev.backline.cli.policy;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunPolicyProfileConverterTest {

    private final RunPolicyProfileConverter converter = new RunPolicyProfileConverter();

    @Test
    void convertDelegatesToProfileParser() {
        assertThat(converter.convert("strict")).isEqualTo(RunPolicyProfile.STRICT);
        assertThat(converter.convert("warn-only")).isEqualTo(RunPolicyProfile.WARN_ONLY);
    }

    @Test
    void convertRejectsUnknownPreset() {
        assertThatThrownBy(() -> converter.convert("lenient"))
                .isInstanceOf(CommandLine.TypeConversionException.class)
                .hasMessageContaining("Invalid policy preset");
    }
}
