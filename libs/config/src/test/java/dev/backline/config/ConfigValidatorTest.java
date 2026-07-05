package dev.backline.config;

import dev.backline.config.model.BacklineConfig;
import dev.backline.config.model.CheckDefinition;
import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.check.HttpMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigValidatorTest {

    @Test
    void rejectsBlankProject() {
        BacklineConfig cfg = new BacklineConfig(" ", "e", List.of(sampleCheck()));
        assertThatThrownBy(() -> ConfigValidator.validate(cfg)).isInstanceOf(ConfigParseException.class);
    }

    @Test
    void rejectsBlankEnvironment() {
        BacklineConfig cfg = new BacklineConfig("p", " ", List.of(sampleCheck()));
        assertThatThrownBy(() -> ConfigValidator.validate(cfg)).isInstanceOf(ConfigParseException.class);
    }

    @Test
    void rejectsEmptyChecks() {
        BacklineConfig cfg = new BacklineConfig("p", "e", List.of());
        assertThatThrownBy(() -> ConfigValidator.validate(cfg)).isInstanceOf(ConfigParseException.class);
    }

    @Test
    void rejectsNullMethod() {
        CheckDefinition bad = new CheckDefinition("a", "n", null, "http://localhost/x", 200, null, null);
        BacklineConfig cfg = new BacklineConfig("p", "e", List.of(bad));
        assertThatThrownBy(() -> ConfigValidator.validate(cfg)).isInstanceOf(ConfigParseException.class);
    }

    @Test
    void rejectsMalformedUrl() {
        CheckDefinition bad = new CheckDefinition("a", "n", HttpMethod.GET, ":::not-a-uri", 200, null, null);
        BacklineConfig cfg = new BacklineConfig("p", "e", List.of(bad));
        assertThatThrownBy(() -> ConfigValidator.validate(cfg)).isInstanceOf(ConfigParseException.class);
    }

    @Test
    void rejectsRelativeUrl() {
        CheckDefinition bad = new CheckDefinition("a", "n", HttpMethod.GET, "/relative", 200, null, null);
        BacklineConfig cfg = new BacklineConfig("p", "e", List.of(bad));
        assertThatThrownBy(() -> ConfigValidator.validate(cfg)).isInstanceOf(ConfigParseException.class);
    }

    @Test
    void rejectsHostlessHttpUrl() {
        CheckDefinition bad = new CheckDefinition("a", "n", HttpMethod.GET, "http:///health", 200, null, null);
        BacklineConfig cfg = new BacklineConfig("p", "e", List.of(bad));
        assertThatThrownBy(() -> ConfigValidator.validate(cfg)).isInstanceOf(ConfigParseException.class);
    }

    @Test
    void rejectsExpectedStatusTooLow() {
        CheckDefinition bad = new CheckDefinition("a", "n", HttpMethod.GET, "http://localhost/x", 99, null, null);
        BacklineConfig cfg = new BacklineConfig("p", "e", List.of(bad));
        assertThatThrownBy(() -> ConfigValidator.validate(cfg)).isInstanceOf(ConfigParseException.class);
    }

    @Test
    void rejectsExpectedStatusTooHigh() {
        CheckDefinition bad = new CheckDefinition("a", "n", HttpMethod.GET, "http://localhost/x", 600, null, null);
        BacklineConfig cfg = new BacklineConfig("p", "e", List.of(bad));
        assertThatThrownBy(() -> ConfigValidator.validate(cfg)).isInstanceOf(ConfigParseException.class);
    }

    @Test
    void rejectsMaxLatencyZero() {
        CheckDefinition bad = new CheckDefinition("a", "n", HttpMethod.GET, "http://localhost/x", 200, 0, null);
        BacklineConfig cfg = new BacklineConfig("p", "e", List.of(bad));
        assertThatThrownBy(() -> ConfigValidator.validate(cfg)).isInstanceOf(ConfigParseException.class);
    }

    @Test
    void rejectsMaxLatencyNegative() {
        CheckDefinition bad = new CheckDefinition("a", "n", HttpMethod.GET, "http://localhost/x", 200, -1, null);
        BacklineConfig cfg = new BacklineConfig("p", "e", List.of(bad));
        assertThatThrownBy(() -> ConfigValidator.validate(cfg)).isInstanceOf(ConfigParseException.class);
    }

    @Test
    void rejectsDuplicateKeys() {
        CheckDefinition a = sampleCheck();
        CheckDefinition b = sampleCheck();
        BacklineConfig cfg = new BacklineConfig("p", "e", List.of(a, b));
        assertThatThrownBy(() -> ConfigValidator.validate(cfg)).isInstanceOf(ConfigParseException.class);
    }

    @Test
    void rejectsAssertionWithoutPath() {
        CheckDefinition bad =
                new CheckDefinition("a", "n", HttpMethod.GET, "http://localhost/x", 200, null, List.of(new AssertionDto(null, 1, null)));
        BacklineConfig cfg = new BacklineConfig("p", "e", List.of(bad));
        assertThatThrownBy(() -> ConfigValidator.validate(cfg)).isInstanceOf(ConfigParseException.class);
    }

    @Test
    void rejectsAssertionWithoutEqualsOrExists() {
        CheckDefinition bad = new CheckDefinition(
                "a",
                "n",
                HttpMethod.GET,
                "http://localhost/x",
                200,
                null,
                List.of(new AssertionDto("$.x", null, null)));
        BacklineConfig cfg = new BacklineConfig("p", "e", List.of(bad));
        assertThatThrownBy(() -> ConfigValidator.validate(cfg)).isInstanceOf(ConfigParseException.class);
    }

    private static CheckDefinition sampleCheck() {
        return new CheckDefinition("a", "n", HttpMethod.GET, "http://localhost/x", 200, null, null);
    }
}
