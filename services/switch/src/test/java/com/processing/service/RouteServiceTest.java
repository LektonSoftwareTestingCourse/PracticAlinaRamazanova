package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.common.dto.transactionlogger.TransactionRequest;
import com.processing.exception.AuthorizationException;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    private static final String PAN = "4000001234567890";
    private static final String STAN = "000001";
    private static final String RRN = "123456789012";

    @Mock
    private RoutingService routingService;
    @Mock
    private AuthorizationClient authorizationClient;
    @Mock
    private AcquiringFeeClient acquiringFeeClient;
    @Mock
    private LoggerClient loggerClient;

    private RouteService routeService;

    @BeforeEach
    void setUp() {
        routeService = new RouteService(routingService, authorizationClient, acquiringFeeClient, loggerClient);
    }

    @Test
    void shouldRouteApprovedTransactionAndLogIt() {
        AuthorizationResponse approved = response("APPROVED", RRN, 12);
        when(routingService.getIssuerIdByPan(PAN)).thenReturn("ISS001");
        when(authorizationClient.authorize(any())).thenReturn(approved);
        when(acquiringFeeClient.fetchAcquiringFee(any(), eq(STAN), eq(PAN), eq("TERM0001"), eq(new BigDecimal("100"))))
                .thenReturn(new BigDecimal("2.50"));
        when(loggerClient.log(any())).thenReturn(true);

        AuthorizationResponse result = routeService.route(request());

        assertThat(result).isEqualTo(approved);
        ArgumentCaptor<AuthorizationRequest> requestCaptor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorizationClient).authorize(requestCaptor.capture());
        assertThat(requestCaptor.getValue().issuerId()).isEqualTo("ISS001");
        assertThat(requestCaptor.getValue().terminalId()).isEqualTo("TERM0001");
        assertThat(requestCaptor.getValue().merchantId()).isEqualTo("MERCH1234567890");
        ArgumentCaptor<TransactionRequest> transactionCaptor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(loggerClient).log(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().issuerId()).isEqualTo("ISS001");
        assertThat(transactionCaptor.getValue().acquiringFee()).isEqualByComparingTo("2.50");
    }

    @Test
    void shouldReturnUnknownBinDeclineAndLogIt() {
        when(routingService.getIssuerIdByPan(PAN)).thenThrow(new UnknownBinException("400000"));
        when(loggerClient.log(any())).thenReturn(true);

        AuthorizationResponse result = routeService.route(request());

        assertThat(result.status()).isEqualTo("DECLINED");
        assertThat(result.declineReason()).isEqualTo("CARD_NOT_FOUND");
        verify(authorizationClient, never()).authorize(any());
        verify(loggerClient).log(any());
    }

    @Test
    void shouldHandleNullPanAsUnknownBin() {
        AuthorizationRequest request = request(null);
        when(routingService.getIssuerIdByPan(null)).thenThrow(new UnknownBinException("null-or-short"));
        when(loggerClient.log(any())).thenReturn(true);

        assertThat(routeService.route(request).status()).isEqualTo("DECLINED");
    }

    @Test
    void shouldHandleShortPanAsUnknownBin() {
        AuthorizationRequest shortPanRequest = request("12345");
        when(routingService.getIssuerIdByPan("12345")).thenThrow(new UnknownBinException("12345"));
        when(loggerClient.log(any())).thenReturn(true);

        assertThat(routeService.route(shortPanRequest).status()).isEqualTo("DECLINED");
    }

    @Test
    void shouldReturnAuthorizationUnavailableWhenAuthorizationFails() {
        when(routingService.getIssuerIdByPan(PAN)).thenReturn("ISS001");
        when(authorizationClient.authorize(any())).thenThrow(new AuthorizationException(STAN, 1, "down"));
        when(loggerClient.log(any())).thenReturn(true);

        AuthorizationResponse result = routeService.route(request());

        assertThat(result.status()).isEqualTo("DECLINED");
        assertThat(result.declineReason()).isEqualTo("Authorization service unavailable");
    }

    @Test
    void shouldRollbackApprovedTransactionWhenLoggingFails() {
        AuthorizationResponse approved = response("APPROVED", RRN, null);
        when(routingService.getIssuerIdByPan(PAN)).thenReturn("ISS001");
        when(authorizationClient.authorize(any())).thenReturn(approved);
        when(acquiringFeeClient.fetchAcquiringFee(any(), any(), any(), any(), any())).thenReturn(null);
        when(loggerClient.log(any())).thenReturn(false);
        when(authorizationClient.rollback(any(), eq(RRN))).thenReturn(
                new RollbackResponse(RRN, "00", "APPROVED", null, 1));

        AuthorizationResponse result = routeService.route(request());

        assertThat(result.responseCode()).isEqualTo("96");
        assertThat(result.status()).isEqualTo("DECLINED");
        verify(authorizationClient).rollback(any(), eq(RRN));
    }

    @Test
    void shouldReturnSystemErrorWhenRollbackHasNoResponse() {
        when(routingService.getIssuerIdByPan(PAN)).thenReturn("ISS001");
        when(authorizationClient.authorize(any())).thenReturn(response("APPROVED", RRN, 1));
        when(acquiringFeeClient.fetchAcquiringFee(any(), any(), any(), any(), any())).thenReturn(null);
        when(loggerClient.log(any())).thenReturn(false);
        when(authorizationClient.rollback(any(), eq(RRN))).thenReturn(null);

        assertThat(routeService.route(request()).responseCode()).isEqualTo("96");
    }

    @Test
    void shouldKeepDeclinedResponseWhenLoggingFails() {
        AuthorizationResponse declined = response("DECLINED", null, 1);
        when(routingService.getIssuerIdByPan(PAN)).thenReturn("ISS001");
        when(authorizationClient.authorize(any())).thenReturn(declined);
        when(acquiringFeeClient.fetchAcquiringFee(any(), any(), any(), any(), any())).thenReturn(null);
        when(loggerClient.log(any())).thenReturn(false);

        assertThat(routeService.route(request())).isEqualTo(declined);
        verify(authorizationClient, never()).rollback(any(), any());
    }

    @Test
    void shouldLogDeclinedRollbackWithoutChangingSystemError() {
        when(routingService.getIssuerIdByPan(PAN)).thenReturn("ISS001");
        when(authorizationClient.authorize(any())).thenReturn(response("APPROVED", RRN, 1));
        when(acquiringFeeClient.fetchAcquiringFee(any(), any(), any(), any(), any())).thenReturn(null);
        when(loggerClient.log(any())).thenReturn(false);
        when(authorizationClient.rollback(any(), eq(RRN))).thenReturn(
                new RollbackResponse(RRN, "05", "DECLINED", "ROLLBACK_FAILED", 1));

        assertThat(routeService.route(request()).responseCode()).isEqualTo("96");
    }

    private static AuthorizationRequest request() {
        return request(PAN);
    }

    private static AuthorizationRequest request(String pan) {
        return AuthorizationRequest.builder()
                .mti("0100")
                .stan(STAN)
                .pan(pan)
                .processingCode("000000")
                .amount(new BigDecimal("100"))
                .currencyCode("643")
                .transmissionDateTime(Instant.parse("2026-11-05T10:00:00Z"))
                .terminalId("TERM0001")
                .terminalType("POS")
                .merchantId("MERCH01234567890")
                .mcc("5045")
                .acquirerId("ACQ002")
                .issuerId(null)
                .build();
    }

    private static AuthorizationResponse response(String status, String rrn, Integer processingTimeMs) {
        return new AuthorizationResponse(
                "0110", STAN, rrn, status.equals("APPROVED") ? "AUTH01" : null,
                status.equals("APPROVED") ? "00" : "05", status, null, processingTimeMs);
    }
}
