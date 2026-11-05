package com.processing.gateway.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogDataMaskerTest {

    private final LogDataMasker masker = new LogDataMasker();

    @Test
    void shouldMaskPanAndCvv() {
        String result = masker.maskData("{\"pan\":\"4000001234567890\",\"cvv\":\"123\"}");

        assertThat(result).contains("\"pan\":\"400000****7890\"");
        assertThat(result).contains("\"cvv\":\"***\"");
    }

    @Test
    void shouldKeepDataWithoutSensitiveFields() {
        String data = "{\"stan\":\"000001\"}";

        assertThat(masker.maskData(data)).isEqualTo(data);
    }
}
