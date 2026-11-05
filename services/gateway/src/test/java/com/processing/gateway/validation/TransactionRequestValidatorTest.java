package com.processing.gateway.validation;

import com.processing.common.dto.authorization.AuthorizationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class TransactionRequestValidatorTest {

    private final TransactionRequestValidator validator = new TransactionRequestValidator();

    @Test
    void shouldAcceptValidRequest() {
        assertThatCode(() -> validator.validate(request())).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMissingRequest() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Request body is required");
    }

    @Test
    void shouldRejectMissingRequiredField() {
        assertThatThrownBy(() -> validator.validate(withMissingMti()))
                .isInstanceOf(TransactionValidationException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "0200, 4000001234567890, Field 'mti' must be '0100'",
            "0100, 123, Field 'pan' must be exactly 16 digits"
    })
    void shouldRejectInvalidMessageTypeOrPan(String mti, String pan, String expectedMessage) {
        assertThatThrownBy(() -> validator.validate(request(mti, pan, new BigDecimal("100"), "643", "5045")))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage(expectedMessage);
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> validator.validate(withAmount(BigDecimal.ZERO)))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Field 'amount' must be > 0");
    }

    @Test
    void shouldRejectInvalidCurrencyCode() {
        assertThatThrownBy(() -> validator.validate(withCurrencyCode("64")))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Field 'currencyCode' must be exactly 3 characters");
    }

    @Test
    void shouldRejectInvalidMerchantCategoryCode() {
        assertThatThrownBy(() -> validator.validate(withMcc("abc")))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Field 'mcc' must be exactly 4 digits");
    }

    private static AuthorizationRequest request() {
        return request("0100", "4000001234567890", new BigDecimal("100"), "643", "5045");
    }

    private static AuthorizationRequest request(
            String mti, String pan, BigDecimal amount, String currencyCode, String mcc) {
        return AuthorizationRequest.builder()
                .mti(mti)
                .stan("000001")
                .pan(pan)
                .processingCode("000000")
                .amount(amount)
                .currencyCode(currencyCode)
                .transmissionDateTime(Instant.parse("2026-11-05T10:00:00Z"))
                .terminalId("TERM0001")
                .terminalType("POS")
                .merchantId("MERCH1234567890")
                .mcc(mcc)
                .acquirerId("ACQ002")
                .issuerId("ISS001")
                .build();
    }

    private static AuthorizationRequest withMissingMti() {
        return request(null, "4000001234567890", new BigDecimal("100"), "643", "5045");
    }

    private static AuthorizationRequest withAmount(BigDecimal amount) {
        return request("0100", "4000001234567890", amount, "643", "5045");
    }

    private static AuthorizationRequest withCurrencyCode(String currencyCode) {
        return request("0100", "4000001234567890", new BigDecimal("100"), currencyCode, "5045");
    }

    private static AuthorizationRequest withMcc(String mcc) {
        return request("0100", "4000001234567890", new BigDecimal("100"), "643", mcc);
    }

}
