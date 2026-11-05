package com.processing.service;

import com.processing.common.dto.transactionlogger.TransactionRequest;
import com.processing.common.dto.transactionlogger.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.mockito.ArgumentMatchers.eq;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoggerClientTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private LoggerClient loggerClient;

    @BeforeEach
    void setUp() {
        loggerClient = new LoggerClient(rabbitTemplate);
    }

    @Test
    void shouldReturnTrueWhenMessageIsPublished() {
        TransactionRequest transaction = transaction();

        assertThat(loggerClient.log(transaction)).isTrue();
        verify(rabbitTemplate).convertAndSend("smp.transactions", "transaction.log", transaction);
    }

    @Test
    void shouldReturnFalseWhenRabbitMqFails() {
        TransactionRequest transaction = transaction();
        doThrow(new AmqpException("broker unavailable"))
                .when(rabbitTemplate).convertAndSend(
                        eq("smp.transactions"), eq("transaction.log"), any(TransactionRequest.class));

        assertThat(loggerClient.log(transaction)).isFalse();
    }

    private static TransactionRequest transaction() {
        Instant now = Instant.parse("2026-11-05T10:00:00Z");
        return new TransactionRequest(
                UUID.randomUUID(), "0100", "000001", null, "4000001234567890", "000000",
                new BigDecimal("100"), "643", "TERM0001", "POS", "MERCH1234567890", "5045",
                "ACQ002", "ISS001", null, TransactionStatus.APPROVED, null, "AUTH01", 1, now, now);
    }
}
