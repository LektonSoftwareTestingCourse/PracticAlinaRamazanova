package com.processing.service;

import com.processing.config.RetryFactory;
import com.processing.config.SwitchProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetryFactoryTest {

    @Test
    void shouldUseConfiguredBackoffIntervals() {
        SwitchProperties properties = properties(List.of(10L, 20L));

        var retry = RetryFactory.authorizationRetry(properties);

        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(2);
        assertThat(retry.getRetryConfig().getIntervalFunction().apply(1)).isEqualTo(10);
        assertThat(retry.getRetryConfig().getIntervalFunction().apply(2)).isEqualTo(20);
        assertThat(retry.getRetryConfig().getIntervalFunction().apply(3)).isEqualTo(20);
    }

    @Test
    void shouldUseMinimumBackoffWhenListIsEmpty() {
        SwitchProperties properties = properties(List.of());

        var retry = RetryFactory.loggerRetry(properties);

        assertThat(retry.getRetryConfig().getIntervalFunction().apply(1)).isEqualTo(1);
    }

    @Test
    void shouldUseMinimumBackoffWhenListIsNull() {
        SwitchProperties properties = properties(null);

        var retry = RetryFactory.loggerRetry(properties);

        assertThat(retry.getRetryConfig().getIntervalFunction().apply(1)).isEqualTo(1);
    }

    private static SwitchProperties properties(List<Long> backoff) {
        return new SwitchProperties(
                "1.0.0",
                Map.of(),
                "http://authorization",
                "http://logger",
                "http://acquirer",
                new SwitchProperties.HttpProperties(100, 100, 100),
                new SwitchProperties.RetryProperties(2, backoff),
                new SwitchProperties.CircuitBreakerSection(
                        new SwitchProperties.CircuitBreakerProperties(50, 10, 5, 1000)));
    }
}
