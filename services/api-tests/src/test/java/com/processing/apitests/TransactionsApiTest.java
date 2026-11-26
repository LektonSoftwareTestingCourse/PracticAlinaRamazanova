package com.processing.apitests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;

class TransactionsApiTest extends ApiTestSupport {

    @Test
    void postTransaction_approvedResponseAndLoggerEffect() {
        TestCard card = createCard();

        given()
                .spec(GATEWAY)
                .body(authorizationRequest(card.pan(), new BigDecimal("100")))
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("mti", equalTo("0100"))
                .body("status", equalTo("APPROVED"))
                .body("responseCode", equalTo("00"))
                .body("stan", matchesPattern("^[0-9]{6}$"))
                .body("rrn", matchesPattern("^[0-9]{12}$"))
                .body("authCode", matchesPattern("^[A-Z0-9]{6}$"))
                .body("processingTimeMs", greaterThanOrEqualTo(0))
                .body("declineReason", nullValue());

        awaitTransaction(card.pan());
    }

    @Test
    void postTransaction_insufficientFunds_returnsDeclinedOutcome() {
        TestCard card = createCard(new BigDecimal("50"), new BigDecimal("1000"), new BigDecimal("30000"));

        given()
                .spec(GATEWAY)
                .body(authorizationRequest(card.pan(), new BigDecimal("100")))
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("51"))
                .body("declineReason", equalTo("INSUFFICIENT_FUNDS"))
                .body("rrn", nullValue());

        awaitTransaction(card.pan());
    }

    @Test
    void postTransaction_exceedsDailyLimit_returnsDeclinedOutcome() {
        TestCard card = createCard(new BigDecimal("100000"), new BigDecimal("100"), new BigDecimal("1000"));

        given()
                .spec(GATEWAY)
                .body(authorizationRequest(card.pan(), new BigDecimal("101")))
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("61"))
                .body("declineReason", equalTo("EXCEEDS_AMOUNT_LIMIT"));

        awaitTransaction(card.pan());
    }

    @Test
    void postTransaction_blockedCard_returnsDeclinedOutcome() {
        TestCard card = createCard();
        given().spec(GATEWAY).body(Map.of("status", "BLOCKED"))
                .patch("/api/cards/{pan}", card.pan()).then().statusCode(200);

        given()
                .spec(GATEWAY)
                .body(authorizationRequest(card.pan(), new BigDecimal("100")))
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(200)
                .body("responseCode", equalTo("05"))
                .body("declineReason", equalTo("CARD_BLOCKED"));

        awaitTransaction(card.pan());
    }

    @Test
    void postTransaction_inactiveCard_returnsDeclinedOutcome() {
        TestCard card = createCard();
        given().spec(GATEWAY).body(Map.of("status", "INACTIVE"))
                .patch("/api/cards/{pan}", card.pan()).then().statusCode(200);

        given()
                .spec(GATEWAY)
                .body(authorizationRequest(card.pan(), new BigDecimal("100")))
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(200)
                .body("responseCode", equalTo("05"))
                .body("declineReason", equalTo("CARD_INACTIVE"));

        awaitTransaction(card.pan());
    }

    @Test
    void postTransaction_expiredCardState_returnsDeclinedOutcome() {
        TestCard card = createCard();
        given().spec(GATEWAY).body(Map.of("status", "EXPIRED"))
                .patch("/api/cards/{pan}", card.pan()).then().statusCode(200);

        given()
                .spec(GATEWAY)
                .body(authorizationRequest(card.pan(), new BigDecimal("100")))
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(200)
                .body("responseCode", equalTo("54"))
                .body("declineReason", equalTo("CARD_EXPIRED"));

        awaitTransaction(card.pan());
    }

    @Test
    void postTransaction_expiredByTransmissionDate_returnsDeclinedOutcome() {
        TestCard card = createCard();
        Map<String, Object> request = authorizationRequest(card.pan(), new BigDecimal("100"));
        request.put("transmissionDateTime", Instant.parse("2035-01-01T00:00:00Z").toString());

        given()
                .spec(GATEWAY)
                .body(request)
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(200)
                .body("responseCode", equalTo("54"))
                .body("declineReason", equalTo("CARD_EXPIRED"));

        awaitTransaction(card.pan());
    }

    @Test
    void postTransaction_unknownBin_returnsCardNotFoundOutcome() {
        String pan = unknownBinPan();
        given()
                .spec(GATEWAY)
                .body(authorizationRequest(pan, new BigDecimal("100")))
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("14"))
                .body("declineReason", equalTo("CARD_NOT_FOUND"));

        awaitTransaction(pan);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "400000123456789A"})
    void postTransaction_invalidPan_returnsGatewayValidationError(String pan) {
        Map<String, Object> request = authorizationRequest(pan, new BigDecimal("100"));

        given()
                .spec(GATEWAY)
                .body(request)
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("serviceName", equalTo("gateway"))
                .body("message", containsString("pan"));

        awaitNoTransaction(pan);
    }


    @ParameterizedTest
    @CsvSource({
            "mti,0120",
            "pan,123",
            "processingCode, ",
            "currencyCode,64",
            "terminalId, ",
            "merchantId, ",
            "mcc,123",
            "acquirerId, "
    })
    void postTransaction_invalidField_returnsGatewayValidationError(String field, String value) {
        String pan = unknownPan();
        Map<String, Object> request = authorizationRequest(pan, new BigDecimal("100"));
        request.put(field, value == null || value.isBlank() ? " " : value);

        given()
                .spec(GATEWAY)
                .body(request)
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("serviceName", equalTo("gateway"))
                .body("message", containsString(field));

        awaitNoTransaction(field.equals("pan") ? (String) request.get("pan") : pan);
    }

    @Test
    void postTransaction_missingTransmissionDateTime_returnsGatewayValidationError() {
        String pan = unknownPan();
        Map<String, Object> request = authorizationRequest(pan, new BigDecimal("100"));
        request.put("transmissionDateTime", null);

        given()
                .spec(GATEWAY)
                .body(request)
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("serviceName", equalTo("gateway"))
                .body("message", containsString("transmissionDateTime"));

        awaitNoTransaction(pan);
    }

    @Test
    void postTransaction_nonPositiveAmount_returnsGatewayValidationError() {
        TestCard card = createCard();
        Map<String, Object> request = authorizationRequest(card.pan(), BigDecimal.ZERO);

        given()
                .spec(GATEWAY)
                .body(request)
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("amount"));

        awaitNoTransaction(card.pan());
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "DECLINED"})
    void searchTransactions_acceptsStatusFilter(String status) {
        given()
                .spec(GATEWAY)
                .queryParam("status", status)
                .queryParam("limit", 10)
                .queryParam("offset", 0)
                .when()
                .get("/api/transactions/search")
                .then()
                .statusCode(200)
                .body("total", greaterThanOrEqualTo(0))
                .body("transactions", notNullValue());
    }

    @Test
    void searchTransactions_filtersByDateMerchantAndDeclineReason() {
        TestCard card = createCard(new BigDecimal("50"), new BigDecimal("1000"), new BigDecimal("30000"));
        given().spec(GATEWAY)
                .body(authorizationRequest(card.pan(), new BigDecimal("100")))
                .post("/api/transactions").then().statusCode(200);
        awaitTransaction(card.pan());

        given()
                .spec(GATEWAY)
                .queryParam("pan", card.pan())
                .queryParam("status", "DECLINED")
                .queryParam("dateFrom", LocalDate.now().minusDays(1).toString())
                .queryParam("dateTo", LocalDate.now().plusDays(1).toString())
                .queryParam("merchantId", "MERCH0000000001")
                .queryParam("issuerId", "ISS001")
                .queryParam("mcc", "5411")
                .queryParam("declineReason", "INSUFFICIENT_FUNDS")
                .queryParam("limit", 10)
                .queryParam("offset", 0)
                .when()
                .get("/api/transactions/search")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("transactions", hasSize(1))
                .body("transactions[0].declineReason", equalTo("INSUFFICIENT_FUNDS"));
    }

    @Test
    void searchTransactions_invalidDateRange_returns400() {
        given()
                .spec(GATEWAY)
                .queryParam("dateFrom", "2026-11-27")
                .queryParam("dateTo", "2026-11-25")
                .when()
                .get("/api/transactions/search")
                .then()
                .statusCode(400)
                .body("message", notNullValue());
    }

    @Test
    void searchTransactions_emptySelection_returnsEmptyArray() {
        given()
                .spec(GATEWAY)
                .queryParam("pan", unknownPan())
                .when()
                .get("/api/transactions/search")
                .then()
                .statusCode(200)
                .body("total", equalTo(0))
                .body("transactions", hasSize(0));
    }

    @ParameterizedTest
    @CsvSource({
            "limit,0",
            "limit,-1",
            "limit,501",
            "offset,-1"
    })
    void searchTransactions_invalidPagination_returns400(String parameter, int value) {
        given()
                .spec(GATEWAY)
                .queryParam(parameter, value)
                .when()
                .get("/api/transactions/search")
                .then()
                .statusCode(400)
                .body("message", containsString(parameter));
    }

    @Test
    void exportTransactions_returnsCsvContract() {
        given()
                .spec(GATEWAY)
                .accept("text/csv")
                .when()
                .get("/api/transactions/export")
                .then()
                .statusCode(200)
                .contentType(containsString("text/csv"))
                .header("Content-Disposition", containsString("transactions.csv"));
    }
}
