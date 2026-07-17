package dev.backline.core.constants;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConstantsTest {

    @Test
    void sensitiveHeadersAreLowercaseCanonicalNames() {
        assertThat(SensitiveHeaders.NAMES)
                .containsExactlyInAnyOrder("authorization", "cookie", "set-cookie");
    }

    @Test
    void responsePreviewLimitIsBounded() {
        assertThat(ResponseLimits.RESPONSE_PREVIEW_MAX_BYTES).isEqualTo(4096);
    }
}
