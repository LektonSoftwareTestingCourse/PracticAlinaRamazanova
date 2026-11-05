package com.processing.gateway.ratelimit;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

    @Test
    void shouldConsumeTokensAndRefillAfterElapsedTime() {
        AtomicLong now = new AtomicLong();
        Ticker ticker = now::get;
        InMemoryRateLimiter rateLimiter = InMemoryRateLimiter.forTesting(
                2, 1, Duration.ofMinutes(1), 10, ticker);

        assertThat(rateLimiter.allowRequest("transactions:127.0.0.1")).isTrue();
        assertThat(rateLimiter.allowRequest("transactions:127.0.0.1")).isTrue();
        assertThat(rateLimiter.allowRequest("transactions:127.0.0.1")).isFalse();

        now.set(1_000_000_000L);

        assertThat(rateLimiter.allowRequest("transactions:127.0.0.1")).isTrue();
    }
}
