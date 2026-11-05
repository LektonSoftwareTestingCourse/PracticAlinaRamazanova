package com.processing.service;

import com.processing.config.SwitchProperties;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchantAcquirerClientTest {

    @Test
    void shouldReturnNullWhenFeeResponseIsEmpty() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec postSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(postSpec);
        when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        when(postSpec.retrieve()).thenReturn(responseSpec);
        MerchantAcquirerClient client = new MerchantAcquirerClient(properties(), restClient);

        assertThat(client.fetchAcquiringFee(
                Instant.parse("2026-11-05T10:00:00Z"), "000001", "4000001234567890", "TERM0001",
                new BigDecimal("100"))).isNull();
    }

    @Test
    void shouldReturnAcquiringFeeWhenResponseIsPresent() throws Exception {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec postSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(postSpec);
        when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        when(postSpec.retrieve()).thenReturn(responseSpec);
        Class<?> responseClass = Arrays.stream(MerchantAcquirerClient.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("AcquirerFeeResponse"))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = responseClass.getDeclaredConstructor(BigDecimal.class);
        constructor.setAccessible(true);
        Object response = constructor.newInstance(new BigDecimal("2.50"));
        @SuppressWarnings("unchecked")
        Class<Object> responseType = (Class<Object>) responseClass;
        when(responseSpec.body(responseType)).thenReturn(response);
        MerchantAcquirerClient client = new MerchantAcquirerClient(properties(), restClient);

        assertThat(client.fetchAcquiringFee(
                Instant.parse("2026-11-05T10:00:00Z"), "000001", "4000001234567890", "TERM0001",
                new BigDecimal("100"))).isEqualByComparingTo("2.50");
    }

    @Test
    void shouldReturnNullWhenAcquirerIsUnavailable() {
        RestClient restClient = mock(RestClient.class);
        when(restClient.post()).thenThrow(new IllegalStateException("down"));
        MerchantAcquirerClient client = new MerchantAcquirerClient(properties(), restClient);

        assertThat(client.fetchAcquiringFee(
                Instant.parse("2026-11-05T10:00:00Z"), "000001", "4000001234567890", "TERM0001",
                new BigDecimal("100"))).isNull();
    }

    private static SwitchProperties properties() {
        return new SwitchProperties(
                "1.0.0", Map.of(), "http://authorization", "http://logger", "http://acquirer",
                new SwitchProperties.HttpProperties(100, 100, 100),
                new SwitchProperties.RetryProperties(1, List.of()),
                new SwitchProperties.CircuitBreakerSection(
                        new SwitchProperties.CircuitBreakerProperties(50, 10, 5, 1000)));
    }
}
