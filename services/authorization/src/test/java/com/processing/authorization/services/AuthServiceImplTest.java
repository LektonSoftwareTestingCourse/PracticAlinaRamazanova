package com.processing.authorization.services;

import com.processing.authorization.client.BinLookupClient;
import com.processing.authorization.client.CardManagementClient;
import com.processing.authorization.events.AuthorizationEventNotifier;
import com.processing.authorization.exceptions.CardNotFoundException;
import com.processing.authorization.exceptions.RollbackConflictException;
import com.processing.authorization.repositories.LimitUsageRepository;
import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackRequest;
import com.processing.common.dto.cardmanagement.CardModel;
import com.processing.common.dto.cardmanagement.CardModelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String PAN = "4000001234567890";
    private static final Instant REQUEST_TIME = Instant.parse("2026-11-05T10:00:00Z");

    @Mock
    private LimitUsageRepository limitUsageRepository;
    @Mock
    private AuthorizationEventNotifier eventNotifier;
    @Mock
    private BinLookupClient binLookupClient;
    @Mock
    private CardManagementClient cardManagementClient;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                limitUsageRepository, eventNotifier, binLookupClient, cardManagementClient);
    }

    @Test
    void shouldApproveAuthorizationWhenCardIsActiveAndLimitsAreAvailable() {
        when(cardManagementClient.getCard(PAN)).thenReturn(card(CardModelStatus.ACTIVE, new BigDecimal("5000")));
        when(binLookupClient.getIssuerId(PAN)).thenReturn(Optional.of("ISS001"));
        when(limitUsageRepository.upsertLimitUsage(
                anyString(), any(LocalDate.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(1);
        when(limitUsageRepository.fetchRrnBlock()).thenReturn(123L);

        AuthorizationResponse response = authService.authorize(request(), REQUEST_TIME);

        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_APPROVED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_APPROVED);
        assertThat(response.rrn()).isNotBlank();
        assertThat(response.authCode()).hasSize(6);
        verify(cardManagementClient).reserve(any(BigDecimal.class), anyString(), anyString());
        verify(eventNotifier).notify(any());
    }

    @Test
    void shouldDeclineWhenCardIsNotFound() {
        when(cardManagementClient.getCard(PAN)).thenThrow(new CardNotFoundException(PAN));

        AuthorizationResponse response = authService.authorize(request(), REQUEST_TIME);

        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.declineReason()).isEqualTo("CARD_NOT_FOUND");
    }

    @ParameterizedTest
    @EnumSource(value = CardModelStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    void shouldDeclineWhenCardStatusIsNotActive(CardModelStatus status) {
        when(cardManagementClient.getCard(PAN)).thenReturn(card(status, new BigDecimal("5000")));
        when(binLookupClient.getIssuerId(PAN)).thenReturn(Optional.empty());

        AuthorizationResponse response = authService.authorize(request(), REQUEST_TIME);

        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        verify(cardManagementClient, never()).reserve(any(BigDecimal.class), anyString(), anyString());
    }

    @Test
    void shouldDeclineWhenBalanceIsInsufficient() {
        when(cardManagementClient.getCard(PAN)).thenReturn(card(CardModelStatus.ACTIVE, new BigDecimal("50")));
        when(binLookupClient.getIssuerId(PAN)).thenReturn(Optional.empty());

        AuthorizationResponse response = authService.authorize(request(), REQUEST_TIME);

        assertThat(response.declineReason()).isEqualTo("INSUFFICIENT_FUNDS");
        verify(limitUsageRepository, never()).upsertLimitUsage(
                anyString(), any(LocalDate.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class));
    }

    @Test
    void shouldDeclineWhenLimitsAreExceeded() {
        when(cardManagementClient.getCard(PAN)).thenReturn(card(CardModelStatus.ACTIVE, new BigDecimal("5000")));
        when(binLookupClient.getIssuerId(PAN)).thenReturn(Optional.empty());
        when(limitUsageRepository.upsertLimitUsage(
                anyString(), any(LocalDate.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(0);

        AuthorizationResponse response = authService.authorize(request(), REQUEST_TIME);

        assertThat(response.declineReason()).isEqualTo("EXCEEDS_AMOUNT_LIMIT");
        verify(cardManagementClient, never()).reserve(any(BigDecimal.class), anyString(), anyString());
    }

    @Test
    void shouldDeclineRollbackWhenTransactionWasAlreadyRolledBack() {
        RollbackRequest request = new RollbackRequest("123456789012", PAN, new BigDecimal("100"));
        org.mockito.Mockito.doThrow(new RollbackConflictException("already rolled back"))
                .when(cardManagementClient).rollback(request);

        var response = authService.rollback(request, REQUEST_TIME);

        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo("ALREADY_ROLLED_BACK");
    }

    private static AuthorizationRequest request() {
        return AuthorizationRequest.builder()
                .mti("0100")
                .stan("000001")
                .pan(PAN)
                .processingCode("000000")
                .amount(new BigDecimal("100"))
                .currencyCode("643")
                .transmissionDateTime(REQUEST_TIME)
                .terminalId("TERM0001")
                .terminalType("POS")
                .merchantId("MERCH0000000002")
                .mcc("5045")
                .acquirerId("ACQ002")
                .issuerId("ISS001")
                .build();
    }

    private static CardModel card(CardModelStatus status, BigDecimal balance) {
        return new CardModel(
                UUID.randomUUID(), PAN, "400000", "IVAN IVANOV", YearMonth.of(2027, 12), status,
                "643", new BigDecimal("1000"), new BigDecimal("5000"), balance, "ISS001", REQUEST_TIME);
    }
}
