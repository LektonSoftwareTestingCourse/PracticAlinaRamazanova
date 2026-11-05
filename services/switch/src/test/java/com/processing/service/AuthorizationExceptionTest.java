package com.processing.service;

import com.processing.exception.AuthorizationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationExceptionTest {

    @Test
    void shouldUseUnknownErrorWhenLastErrorIsNull() {
        AuthorizationException exception = new AuthorizationException("000001", 3, null);

        assertThat(exception.getStan()).isEqualTo("000001");
        assertThat(exception).hasMessageContaining("unknown");
    }
}
