package com.processing.cardmanagement.services;

import com.processing.cardmanagement.events.CardEventNotifier;
import com.processing.cardmanagement.exceptions.CardNotFoundException;
import com.processing.cardmanagement.models.Card;
import com.processing.cardmanagement.models.CardStatus;
import com.processing.cardmanagement.options.CardServiceDefaults;
import com.processing.cardmanagement.options.CardServiceSettings;
import com.processing.cardmanagement.repositories.CardRepository;
import com.processing.cardmanagement.repositories.ReservationRepository;
import com.processing.cardmanagement.repositories.ReservationRollbackRepository;
import com.processing.cardmanagement.services.retries.RetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {

    private static final String PAN = "4000001234567890";

    @Mock
    private CardRepository cardRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationRollbackRepository reservationRollbackRepository;
    @Mock
    private CardServiceSettings settings;
    @Mock
    private CardServiceDefaults defaults;
    @Mock
    private PanGenerator panGenerator;
    @Mock
    private CardEventNotifier eventNotifier;
    @Mock
    private BinIssuerService binIssuerService;
    @Mock
    private RetryService retryService;
    @Mock
    private TransactionRunner transactionRunner;

    private CardServiceImpl cardService;

    @BeforeEach
    void setUp() {
        cardService = new CardServiceImpl(
                cardRepository, reservationRepository, reservationRollbackRepository, settings, defaults,
                panGenerator, eventNotifier, binIssuerService, retryService, transactionRunner);
    }

    @Test
    void shouldReturnCardByPan() {
        Card card = card();
        when(cardRepository.findByPan(PAN)).thenReturn(Optional.of(card));

        assertThat(cardService.getCard(PAN)).isEqualTo(card);
    }

    @Test
    void shouldThrowWhenCardDoesNotExist() {
        when(cardRepository.findByPan(PAN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getCard(PAN)).isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void shouldUseDefaultPaginationValues() {
        when(defaults.pageLimit()).thenReturn(20);
        when(defaults.pageOffset()).thenReturn(0L);
        when(cardRepository.findCards(20, 0L, null, null, null, null, null)).thenReturn(List.of(card()));

        assertThat(cardService.getCards(null, null, null, null, null, null, null)).hasSize(1);
    }

    @Test
    void shouldPatchCardAndNotifyListeners() {
        Card card = card();
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card));
        when(cardRepository.update(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get())
                .when(transactionRunner).runSupplier(any());

        Card updated = cardService.patchCard(
                PAN, CardStatus.BLOCKED, new BigDecimal("200"), null, new BigDecimal("800"));

        assertThat(updated.status()).isEqualTo(CardStatus.BLOCKED);
        assertThat(updated.dailyLimit()).isEqualByComparingTo("200");
        assertThat(updated.monthlyLimit()).isEqualByComparingTo("1000");
        assertThat(updated.availableBalance()).isEqualByComparingTo("800");
        verify(eventNotifier).notifyListeners(any());
    }

    @Test
    void shouldCreateCardAndNotifyListeners() {
        Card savedCard = card();
        when(binIssuerService.getIssuerId("400000")).thenReturn("ISS001");
        when(settings.cardValidityPeriod()).thenReturn(3);
        when(settings.maxCardCreationRetries()).thenReturn(2);
        when(panGenerator.generatePan("400000")).thenReturn(PAN);
        when(retryService.supply(org.mockito.ArgumentMatchers.eq(2), any()))
                .thenAnswer(invocation -> ((Supplier<Card>) invocation.getArgument(1)).get());
        when(cardRepository.create(any(Card.class))).thenReturn(savedCard);

        Card result = cardService.createCard(
                "400000", "IVAN IVANOV", "643", new BigDecimal("1000"),
                new BigDecimal("5000"), new BigDecimal("2000"));

        assertThat(result).isEqualTo(savedCard);
        verify(eventNotifier).notifyListeners(any());
    }

    @Test
    void shouldUpdateCardsByBins() {
        Card card = card();
        when(cardRepository.findCardsByBinsForUpdate(List.of("400000"))).thenReturn(List.of(card));
        when(cardRepository.update(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get())
                .when(transactionRunner).runSupplier(any());

        int updated = cardService.bulkUpdateStatus(List.of("400000"), null, CardStatus.BLOCKED);

        assertThat(updated).isEqualTo(1);
        verify(cardRepository).update(any(Card.class));
    }

    private static Card card() {
        return new Card(
                UUID.randomUUID(), PAN, "400000", "IVAN IVANOV", YearMonth.of(2028, 12), CardStatus.ACTIVE,
                "643", new BigDecimal("100"), new BigDecimal("1000"), new BigDecimal("900"), "ISS001",
                Instant.parse("2026-11-05T10:00:00Z"));
    }
}
