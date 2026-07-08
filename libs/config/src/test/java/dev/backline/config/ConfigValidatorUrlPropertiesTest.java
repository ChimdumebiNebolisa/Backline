package dev.backline.config;

import dev.backline.config.model.BacklineConfig;
import dev.backline.config.model.CheckDefinition;
import dev.backline.core.check.HttpMethod;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigValidatorUrlPropertiesTest {

    @Property
    void rejectsBadUrls(@ForAll("badUrls") String badUrl) {
        CheckDefinition check = new CheckDefinition("a", "n", HttpMethod.GET, badUrl, 200, null, null);
        BacklineConfig config = new BacklineConfig("p", "e", List.of(check), null);

        assertThatThrownBy(() -> ConfigValidator.validate(config))
                .isInstanceOf(ConfigParseException.class)
                .hasMessageContaining("url");
    }

    @Property
    void rejectsNonHttpSchemes(@ForAll("nonHttpSchemes") String scheme) {
        String url = scheme + "://example.com/path";
        CheckDefinition check = new CheckDefinition("a", "n", HttpMethod.GET, url, 200, null, null);
        BacklineConfig config = new BacklineConfig("p", "e", List.of(check), null);

        assertThatThrownBy(() -> ConfigValidator.validate(config))
                .isInstanceOf(ConfigParseException.class)
                .hasMessageContaining("url");
    }

    @Provide
    Arbitrary<String> badUrls() {
        return Arbitraries.of(
                "/relative/path",
                "relative/path",
                "http:///no-host",
                "https:///no-host",
                ":::not-a-uri",
                " ",
                "\t",
                "file:///etc/passwd",
                "javascript:alert(1)");
    }

    @Provide
    Arbitrary<String> nonHttpSchemes() {
        return Arbitraries.of("ftp", "file", "javascript", "data", "ws", "mailto");
    }
}
