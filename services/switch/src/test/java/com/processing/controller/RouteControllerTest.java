package com.processing.controller;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.service.RouteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteControllerTest {

    @Mock
    private RouteService routeService;

    @InjectMocks
    private RouteController routeController;

    @Test
    void shouldReturnRouteResponseForLongPan() {
        AuthorizationRequest request = request("4000001234567890");
        AuthorizationResponse response = response();
        when(routeService.route(request)).thenReturn(response);

        var result = routeController.routeTransaction(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void shouldReturnRouteResponseForShortPan() {
        AuthorizationRequest request = request("1234567");
        AuthorizationResponse response = response();
        when(routeService.route(request)).thenReturn(response);

        var result = routeController.routeTransaction(request);

        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void shouldReturnRouteResponseForNullPan() {
        AuthorizationRequest request = request(null);
        AuthorizationResponse response = response();
        when(routeService.route(request)).thenReturn(response);

        var result = routeController.routeTransaction(request);

        assertThat(result.getBody()).isEqualTo(response);
    }

    private static AuthorizationRequest request(String pan) {
        return AuthorizationRequest.builder()
                .mti("0100")
                .stan("000001")
                .pan(pan)
                .processingCode("000000")
                .amount(new BigDecimal("100"))
                .currencyCode("643")
                .transmissionDateTime(Instant.parse("2026-11-05T10:00:00Z"))
                .terminalId("TERM0001")
                .terminalType("POS")
                .merchantId("MERCH1234567890")
                .mcc("5045")
                .acquirerId("ACQ002")
                .issuerId(null)
                .build();
    }

    private static AuthorizationResponse response() {
        return new AuthorizationResponse("0110", "000001", null, null, "05", "DECLINED", null, 1);
    }
}
