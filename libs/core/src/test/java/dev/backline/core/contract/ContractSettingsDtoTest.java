package dev.backline.core.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractSettingsDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void defaultsEnableWarnSeverityWithEmptyIgnorePaths() throws Exception {
        ContractSettingsDto defaults = ContractSettingsDto.defaults();

        assertThat(defaults.isEnabled()).isTrue();
        assertThat(defaults.resolvedSeverity()).isEqualTo(ContractSeverity.WARN);
        assertThat(defaults.resolvedIgnorePaths()).isEmpty();

        String json = objectMapper.writeValueAsString(defaults);
        ContractSettingsDto parsed = objectMapper.readValue(json, ContractSettingsDto.class);
        assertThat(parsed.severity()).isEqualTo("warn");
    }

    @Test
    void nullFieldsResolveToEnabledWarnAndEmptyIgnorePaths() {
        ContractSettingsDto settings = new ContractSettingsDto(null, null, null);

        assertThat(settings.isEnabled()).isTrue();
        assertThat(settings.resolvedSeverity()).isEqualTo(ContractSeverity.WARN);
        assertThat(settings.resolvedIgnorePaths()).isEmpty();
    }

    @Test
    void blankSeverityDefaultsToWarnAndExplicitDisableIsHonored() {
        assertThat(new ContractSettingsDto(false, "  ", List.of("$.id")).isEnabled()).isFalse();
        assertThat(new ContractSettingsDto(false, "  ", List.of("$.id")).resolvedSeverity())
                .isEqualTo(ContractSeverity.WARN);
        assertThat(new ContractSettingsDto(true, "block", List.of("$.a")).resolvedSeverity())
                .isEqualTo(ContractSeverity.BLOCK);
        assertThat(new ContractSettingsDto(true, "block", List.of("$.a")).resolvedIgnorePaths())
                .containsExactly("$.a");
    }

    @Test
    void unknownSeverityFailsFast() {
        assertThatThrownBy(() -> new ContractSettingsDto(true, "loud", List.of()).resolvedSeverity())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
