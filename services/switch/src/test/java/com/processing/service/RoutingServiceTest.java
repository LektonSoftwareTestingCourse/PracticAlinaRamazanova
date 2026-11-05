package com.processing.service;

import com.processing.config.SwitchProperties;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingServiceTest {

    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        SwitchProperties properties = new SwitchProperties(
                "1.0.0",
                Map.of("400000", "ISS001"),
                "http://authorization",
                "http://logger",
                "http://acquirer",
                new SwitchProperties.HttpProperties(100, 100, 100),
                new SwitchProperties.RetryProperties(1, java.util.List.of()),
                new SwitchProperties.CircuitBreakerSection(
                        new SwitchProperties.CircuitBreakerProperties(50, 10, 5, 1000)));
        routingService = new RoutingService(properties);
    }

    @Test
    void shouldReturnIssuerForKnownBin() {
        assertThat(routingService.getIssuerIdByPan("4000001234567890")).isEqualTo("ISS001");
    }

    @Test
    void shouldRejectNullPan() {
        assertThatThrownBy(() -> routingService.getIssuerIdByPan(null))
                .isInstanceOf(UnknownBinException.class);
    }

    @Test
    void shouldRejectShortPan() {
        assertThatThrownBy(() -> routingService.getIssuerIdByPan("12345"))
                .isInstanceOf(UnknownBinException.class);
    }

    @Test
    void shouldRejectUnknownBin() {
        assertThatThrownBy(() -> routingService.getIssuerIdByPan("9999991234567890"))
                .isInstanceOf(UnknownBinException.class);
    }
}
