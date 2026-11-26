package com.processing.apitests;

import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class InternalApiTest extends ApiTestSupport {

    @Test
    void authorizationHealth_returnsServiceStatus() {
        given()
                .spec(AUTH_SERVICE)
                .when()
                .get("/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("ok"))
                .body("service", equalTo("authorization"));
    }

    @Test
    void switchHealth_returnsServiceStatus() {
        given()
                .spec(SWITCH_SERVICE)
                .when()
                .get("/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("ok"))
                .body("service", equalTo("switch"));
    }

    @Test
    void loggerHealth_returnsTransactionCount() {
        given()
                .spec(LOGGER_SERVICE)
                .when()
                .get("/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("ok"))
                .body("service", equalTo("transaction-logger"))
                .body("transactionsStored", notNullValue());
    }

    @Test
    void authorizeInternal_approvedResponseMatchesSchema() {
        TestCard card = createCard();

        given()
                .spec(AUTH_SERVICE)
                .body(internalAuthorizationRequest(card.pan(), new BigDecimal("100")))
                .when()
                .post("/api/internal/authorize")
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/authorization-response.json"))
                .body("status", equalTo("APPROVED"))
                .body("responseCode", equalTo("00"))
                .body("rrn", matchesPattern("^[0-9]{12}$"))
                .body("authCode", matchesPattern("^[A-Z0-9]{6}$"))
                .body("declineReason", nullValue());
    }


    @ParameterizedTest
    @CsvSource({
            "mti,010",
            "stan,123",
            "pan,123",
            "processingCode,123",
            "currencyCode,64",
            "terminalId,TERM01",
            "merchantId,MERCH01",
            "mcc,123",
            "acquirerId, "
    })
    void authorizeInternal_invalidField_returns400(String field, String value) {
        Map<String, Object> request = internalAuthorizationRequest(unknownPan(), new BigDecimal("100"));
        request.put(field, value == null || value.isBlank() ? " " : value);

        given()
                .spec(AUTH_SERVICE)
                .body(request)
                .when()
                .post("/api/internal/authorize")
                .then()
                .statusCode(400)
                .body(notNullValue());
    }

    @Test
    void authorizeInternal_missingTransmissionDateTime_returns400() {
        Map<String, Object> request = internalAuthorizationRequest(unknownPan(), new BigDecimal("100"));
        request.put("transmissionDateTime", null);

        given()
                .spec(AUTH_SERVICE)
                .body(request)
                .when()
                .post("/api/internal/authorize")
                .then()
                .statusCode(400)
                .body(notNullValue());
    }

    @Test
    void authorizeInternal_negativeAmount_returns400() {
        Map<String, Object> request = internalAuthorizationRequest(unknownPan(), new BigDecimal("-1"));

        given()
                .spec(AUTH_SERVICE)
                .body(request)
                .when()
                .post("/api/internal/authorize")
                .then()
                .statusCode(400)
                .body(notNullValue());
    }

    @Test
    void authorizeInternal_missingIssuerId_returns400() {
        Map<String, Object> request = internalAuthorizationRequest(unknownPan(), new BigDecimal("100"));
        request.remove("issuerId");

        given()
                .spec(AUTH_SERVICE)
                .body(request)
                .when()
                .post("/api/internal/authorize")
                .then()
                .statusCode(400)
                .body(notNullValue());
    }

    @Test
    void authorizeInternal_cardNotFound_returns404Outcome() {
        String pan = unknownPan();

        given()
                .spec(AUTH_SERVICE)
                .body(internalAuthorizationRequest(pan, new BigDecimal("100")))
                .when()
                .post("/api/internal/authorize")
                .then()
                .statusCode(404)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("14"))
                .body("declineReason", equalTo("CARD_NOT_FOUND"));
    }

    @Test
    void routeInternal_routesAuthorizationAndLogsTransaction() {
        TestCard card = createCard();

        given()
                .spec(SWITCH_SERVICE)
                .body(authorizationRequest(card.pan(), new BigDecimal("100")))
                .when()
                .post("/api/internal/route")
                .then()
                .statusCode(200)
                .body("status", equalTo("APPROVED"))
                .body("responseCode", equalTo("00"))
                .body("stan", matchesPattern("^[0-9]{6}$"));

        awaitTransaction(card.pan());
    }

    @Test
    void rollbackInternal_successThenDuplicateConflict() {
        TestCard card = createCard();
        Response authorization = given()
                .spec(AUTH_SERVICE)
                .body(internalAuthorizationRequest(card.pan(), new BigDecimal("100")))
                .post("/api/internal/authorize")
                .then()
                .statusCode(200)
                .extract()
                .response();
        String rrn = authorization.path("rrn");
        Map<String, Object> rollback = Map.of(
                "rrn", rrn,
                "pan", card.pan(),
                "amount", new BigDecimal("100")
        );

        given()
                .spec(AUTH_SERVICE)
                .body(rollback)
                .when()
                .post("/api/internal/rollback")
                .then()
                .statusCode(200)
                .body("status", equalTo("APPROVED"))
                .body("responseCode", equalTo("00"));

        given()
                .spec(AUTH_SERVICE)
                .body(rollback)
                .when()
                .post("/api/internal/rollback")
                .then()
                .statusCode(409)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("05"))
                .body("declineReason", equalTo("ALREADY_ROLLED_BACK"));
    }

    @Test
    void rollbackInternal_missingReservation_returns404() {
        TestCard card = createCard();

        given()
                .spec(AUTH_SERVICE)
                .body(Map.of(
                        "rrn", rrn(),
                        "pan", card.pan(),
                        "amount", new BigDecimal("100")
                ))
                .when()
                .post("/api/internal/rollback")
                .then()
                .statusCode(404)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("14"))
                .body("declineReason", equalTo("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void loggerInternal_duplicateAndConflictingPayload_return409() {
        TestCard card = createCard();
        UUID id = UUID.randomUUID();
        String stan = stan();
        Map<String, Object> request = loggerRequest(card.pan(), id, stan, new BigDecimal("100"));

        given()
                .spec(LOGGER_SERVICE)
                .body(request)
                .when()
                .post("/api/internal/log")
                .then()
                .statusCode(201)
                .body("id", equalTo(id.toString()))
                .body("status", equalTo("stored"));

        given()
                .spec(LOGGER_SERVICE)
                .body(request)
                .when()
                .post("/api/internal/log")
                .then()
                .statusCode(409)
                .body("error", equalTo("Transaction conflict"))
                .body("serviceName", equalTo("transaction-logger"));

        request.put("amount", new BigDecimal("200"));
        given()
                .spec(LOGGER_SERVICE)
                .body(request)
                .when()
                .post("/api/internal/log")
                .then()
                .statusCode(409)
                .body("error", equalTo("Transaction conflict"))
                .body("serviceName", equalTo("transaction-logger"));

        given()
                .spec(GATEWAY)
                .queryParam("pan", card.pan())
                .when()
                .get("/api/transactions/search")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("transactions", hasSize(1))
                .body("transactions.findAll { it.pan == '" + card.pan() + "' }", hasSize(1))
                .body("transactions[0].amount", equalTo(100));
    }

    @Test
    void loggerInternal_invalidAmount_returns400() {
        TestCard card = createCard();
        Map<String, Object> request = loggerRequest(card.pan(), UUID.randomUUID(), stan(), BigDecimal.ZERO);

        given()
                .spec(LOGGER_SERVICE)
                .body(request)
                .when()
                .post("/api/internal/log")
                .then()
                .statusCode(400)
                .body("error", equalTo("Validation error"))
                .body("message", notNullValue())
                .body("retryAfterMs", equalTo("0"));
    }
}
