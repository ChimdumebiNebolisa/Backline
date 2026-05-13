package dev.backline.core.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void allCodesAreNonBlankAndMatchName() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.code()).isNotBlank();
            assertThat(code.code()).isEqualTo(code.name());
        }
    }
}
