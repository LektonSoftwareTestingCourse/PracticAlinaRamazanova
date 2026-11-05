package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.config.SwitchProperties;
import com.processing.exception.AuthorizationException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationClientTest {

    private static final String STAN = "000001";

    private RestClient restClient;
    private RestClient.RequestBodyUriSpec postSpec;
    private RestClient.ResponseSpec responseSpec;
    private AuthorizationClient authorizationClient;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        postSpec = mock(RestClient.RequestBodyUriSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
        circuitBreaker = CircuitBreaker.ofDefaults("authorization-test");
        Retry retry = Retry.of("authorization-test", RetryConfig.custom().maxAttempts(1).build());
        SwitchProperties properties = new SwitchProperties(
                "1.0.0", Map.of(), "http://authorization", "http://logger", "http://acquirer",
                new SwitchProperties.HttpProperties(100, 100, 100),
                new SwitchProperties.RetryProperties(1, List.of()),
                new SwitchProperties.CircuitBreakerSection(
                        new SwitchProperties.CircuitBreakerProperties(50, 10, 5, 1000)));
        authorizationClient = new AuthorizationClient(properties, restClient, retry, circuitBreaker);
        when(restClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(postSpec);
        when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        when(postSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(java.util.function.Predicate.class), any())).thenReturn(responseSpec);
    }

    @Test
    void shouldReturnApprovedAuthorizationResponse() {
        AuthorizationResponse response = new AuthorizationResponse(
                "0110", STAN, "123456789012", "AUTH01", "00", "APPROVED", null, 1);
        when(responseSpec.body(AuthorizationResponse.class)).thenReturn(response);

        assertThat(authorizationClient.authorize(request())).isEqualTo(response);
    }

    @Test
    void shouldThrowWhenAuthorizationResponseIsInvalid() {
        when(responseSpec.body(AuthorizationResponse.class)).thenReturn(null);

        assertThatThrownBy(() -> authorizationClient.authorize(request()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining(STAN);
    }

    @Test
    void shouldReturnDeclinedAuthorizationResponse() {
        AuthorizationResponse response = new AuthorizationResponse(
                "0110", STAN, null, null, "05", "DECLINED", "CARD_BLOCKED", 1);
        when(responseSpec.body(AuthorizationResponse.class)).thenReturn(response);

        assertThat(authorizationClient.authorize(request())).isEqualTo(response);
    }

    @Test
    void shouldUseCauseMessageWhenAuthorizationCallFails() {
        when(responseSpec.body(AuthorizationResponse.class)).thenThrow(
                new IllegalStateException("outer", new IllegalStateException("inner")));

        assertThatThrownBy(() -> authorizationClient.authorize(request()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("inner");
    }

    @Test
    void shouldThrowWhenAuthorizationStatusIsUnknown() {
        AuthorizationResponse response = new AuthorizationResponse(
                "0110", STAN, null, null, "96", "UNKNOWN", null, 1);
        when(responseSpec.body(AuthorizationResponse.class)).thenReturn(response);

        assertThatThrownBy(() -> authorizationClient.authorize(request()))
                .isInstanceOf(AuthorizationException.class);
    }

    @Test
    void shouldThrowWhenCircuitBreakerIsOpen() {
        circuitBreaker.transitionToOpenState();

        assertThatThrownBy(() -> authorizationClient.authorize(request()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Circuit breaker open");
    }

    @Test
    void shouldReturnValidRollbackResponse() {
        RollbackResponse response = new RollbackResponse("123456789012", "00", "APPROVED", null, 1);
        when(responseSpec.body(RollbackResponse.class)).thenReturn(response);

        assertThat(authorizationClient.rollback(request(), "123456789012")).isEqualTo(response);
    }

    @Test
    void shouldReturnDeclinedRollbackResponse() {
        RollbackResponse response = new RollbackResponse("123456789012", "05", "DECLINED", "ALREADY_ROLLED_BACK", 1);
        when(responseSpec.body(RollbackResponse.class)).thenReturn(response);

        assertThat(authorizationClient.rollback(request(), "123456789012")).isEqualTo(response);
    }

    @Test
    void shouldReturnNullWhenRollbackResponseIsInvalid() {
        when(responseSpec.body(RollbackResponse.class)).thenReturn(null);

        assertThat(authorizationClient.rollback(request(), "123456789012")).isNull();
    }

    @Test
    void shouldReturnOkWhenHealthEndpointResponds() {
        RestClient.RequestHeadersUriSpec<?> getSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        doReturn(getSpec).when(restClient).get();
        doReturn(headersSpec).when(getSpec).uri(anyString());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        assertThat(authorizationClient.checkHealth()).isEqualTo("ok");
    }

    @Test
    void shouldReturnDownWhenHealthEndpointFails() {
        RestClient.RequestHeadersUriSpec<?> getSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        doReturn(getSpec).when(restClient).get();
        doReturn(headersSpec).when(getSpec).uri(anyString());
        when(headersSpec.retrieve()).thenThrow(new IllegalStateException("down"));

        assertThat(authorizationClient.checkHealth()).isEqualTo("down");
    }

    private static AuthorizationRequest request() {
        return AuthorizationRequest.builder()
                .mti("0100")
                .stan(STAN)
                .pan("4000001234567890")
                .processingCode("000000")
                .amount(new BigDecimal("100"))
                .currencyCode("643")
                .transmissionDateTime(Instant.parse("2026-11-05T10:00:00Z"))
                .terminalId("TERM0001")
                .terminalType("POS")
                .merchantId("MERCH1234567890")
                .mcc("5045")
                .acquirerId("ACQ002")
                .issuerId("ISS001")
                .build();
    }
}
