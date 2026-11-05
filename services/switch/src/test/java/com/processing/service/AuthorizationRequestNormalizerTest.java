package com.processing.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationRequestNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "TERM, TERM0000",
            "TERM0001, TERM0001",
            "TERM000123, TERM0001"
    })
    void shouldNormalizeTerminalId(String input, String expected) {
        assertThat(AuthorizationRequestNormalizer.normalizeTerminalId(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void shouldKeepEmptyTerminalId(String input) {
        assertThat(AuthorizationRequestNormalizer.normalizeTerminalId(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @CsvSource({
            "SHOP, SHOP00000000000",
            "MERCH1234567890, MERCH1234567890",
            "MERCH01234567890, MERCH1234567890",
            "MERCH11234567890, MERCH1123456789",
            "ABCDEFGHIJKLMNOX, ABCDEFGHIJKLMNO"
    })
    void shouldNormalizeMerchantId(String input, String expected) {
        assertThat(AuthorizationRequestNormalizer.normalizeMerchantId(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void shouldKeepEmptyMerchantId(String input) {
        assertThat(AuthorizationRequestNormalizer.normalizeMerchantId(input)).isEqualTo(input);
    }
}
