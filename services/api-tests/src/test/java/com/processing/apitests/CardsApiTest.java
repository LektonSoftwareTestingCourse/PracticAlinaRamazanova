package com.processing.apitests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.hamcrest.Matchers.startsWith;

class CardsApiTest extends ApiTestSupport {

    @Test
    void createCard_returnsCardContract() {
        given()
                .spec(GATEWAY)
                .body(Map.of(
                        "bin", "400000",
                        "cardholderName", "API TEST",
                        "currencyCode", "643",
                        "dailyLimit", 15000000,
                        "monthlyLimit", 300000000,
                        "initialBalance", 100000000
                ))
                .when()
                .post("/api/cards")
                .then()
                .statusCode(201)
                .contentType(startsWith("application/json"))
                .body("id", matchesPattern("^[0-9a-fA-F-]{36}$"))
                .body("pan", matchesPattern("^400000\\d{10}$"))
                .body("bin", equalTo("400000"))
                .body("status", equalTo("ACTIVE"))
                .body("issuerId", equalTo("ISS001"))
                .body("availableBalance", equalTo(100000000));
    }

    @Test
    void getCard_returnsCreatedCard() {
        TestCard card = createCard();

        given()
                .spec(GATEWAY)
                .when()
                .get("/api/cards/{pan}", card.pan())
                .then()
                .statusCode(200)
                .header("X-Cache", equalTo("MISS"))
                .body("pan", equalTo(card.pan()))
                .body("status", equalTo("ACTIVE"))
                .body("issuerId", equalTo("ISS001"));

        given()
                .spec(GATEWAY)
                .when()
                .get("/api/cards/{pan}", card.pan())
                .then()
                .statusCode(200)
                .header("X-Cache", equalTo("HIT"))
                .body("pan", equalTo(card.pan()));
    }

    @Test
    void getCard_missingPan_returns404ErrorBody() {
        String pan = unknownPan();
        given()
                .spec(GATEWAY)
                .when()
                .get("/api/cards/{pan}", pan)
                .then()
                .statusCode(404)
                .body("serviceName", equalTo("card-management"))
                .body("message", containsString(pan.substring(0, 4) + "********" + pan.substring(12)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "400000123456789A"})
    void getCard_invalidPan_returns400(String pan) {
        given()
                .spec(GATEWAY)
                .when()
                .get("/api/cards/{pan}", pan)
                .then()
                .statusCode(400)
                .body("message", notNullValue())
                .body("serviceName", equalTo("card-management"));
    }

    @Test
    void createCard_invalidCardholderName_returns400() {
        given()
                .spec(GATEWAY)
                .body(Map.of(
                        "bin", "400000",
                        "cardholderName", "api test",
                        "currencyCode", "643",
                        "dailyLimit", 15000000,
                        "monthlyLimit", 300000000,
                        "initialBalance", 100000000
                ))
                .when()
                .post("/api/cards")
                .then()
                .statusCode(400)
                .body("message", containsString("uppercase"))
                .body("serviceName", equalTo("card-management"));
    }


    @ParameterizedTest
    @CsvSource({
            "bin,123",
            "currencyCode,64",
            "dailyLimit,-1",
            "monthlyLimit,-1"
    })
    void createCard_invalidField_returns400(String field, String value) {
        Map<String, Object> request = new java.util.LinkedHashMap<>(Map.of(
                "bin", "400000",
                "cardholderName", "API TEST",
                "currencyCode", "643",
                "dailyLimit", 15000000,
                "monthlyLimit", 300000000,
                "initialBalance", 100000000
        ));
        request.put(field, (field.endsWith("Limit")) ? new BigDecimal(value) : value);

        given()
                .spec(GATEWAY)
                .body(request)
                .when()
                .post("/api/cards")
                .then()
                .statusCode(400)
                .body("message", notNullValue())
                .body("serviceName", equalTo("card-management"));
    }

    @Test
    void generateCards_returnsRequestedNumberOfCards() {
        given()
                .spec(GATEWAY)
                .body(Map.of("count", 2, "bins", List.of("400000")))
                .when()
                .post("/api/cards/generate")
                .then()
                .statusCode(201)
                .body("generated", equalTo(2))
                .body("cards", hasSize(2))
                .body("cards[0].pan", matchesPattern("^400000\\d{10}$"))
                .body("cards[1].pan", matchesPattern("^400000\\d{10}$"))
                .body("cards[0].status", oneOf("ACTIVE", "INACTIVE", "BLOCKED"))
                .body("cards[1].status", oneOf("ACTIVE", "INACTIVE", "BLOCKED"));
    }

    @Test
    void getCards_appliesFiltersAndPagination() {
        createCard();
        createCard();

        given()
                .spec(GATEWAY)
                .queryParam("limit", 1)
                .queryParam("offset", 0)
                .queryParam("status", "ACTIVE")
                .queryParam("bin", "400000")
                .queryParam("issuerId", "ISS001")
                .queryParam("startDate", "2020-01-01T00:00:00Z")
                .queryParam("endDate", "2035-01-01T00:00:00Z")
                .when()
                .get("/api/cards")
                .then()
                .statusCode(200)
                .body("total", greaterThanOrEqualTo(1))
                .body("cards", hasSize(1))
                .body("cards.findAll { it.status == 'ACTIVE' && it.bin == '400000' && it.issuerId == 'ISS001' }", hasSize(1));
    }

    @Test
    void getCards_emptyFilter_returnsEmptyArray() {
        given()
                .spec(GATEWAY)
                .queryParam("issuerId", "ZZZZZZ")
                .when()
                .get("/api/cards")
                .then()
                .statusCode(200)
                .body("total", equalTo(0))
                .body("cards", hasSize(0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void getCards_nonPositiveLimit_returns400(int limit) {
        given()
                .spec(GATEWAY)
                .queryParam("limit", limit)
                .when()
                .get("/api/cards")
                .then()
                .statusCode(400)
                .body("message", notNullValue())
                .body("serviceName", equalTo("card-management"));
    }

    @Test
    void patchCard_updatesCardStatus() {
        TestCard card = createCard();

        given()
                .spec(GATEWAY)
                .body(Map.of("status", "BLOCKED"))
                .when()
                .patch("/api/cards/{pan}", card.pan())
                .then()
                .statusCode(200)
                .body("pan", equalTo(card.pan()))
                .body("status", equalTo("BLOCKED"));
    }

    @Test
    void deleteCard_returns204() {
        TestCard card = createCard();

        given()
                .spec(GATEWAY)
                .when()
                .delete("/api/cards/{pan}", card.pan())
                .then()
                .statusCode(204);
    }

    @Test
    void reserveAndRollback_restoreAvailableBalance() {
        TestCard card = createCard();
        String rrn = rrn();

        given()
                .spec(GATEWAY)
                .body(Map.of("amount", new BigDecimal("100"), "rrn", rrn))
                .when()
                .post("/api/cards/{pan}/reserve", card.pan())
                .then()
                .statusCode(200)
                .body("availableBalance", equalTo(99999900));

        given()
                .spec(GATEWAY)
                .body(Map.of("amount", new BigDecimal("100"), "rrn", rrn, "pan", card.pan()))
                .when()
                .post("/api/cards/{pan}/rollback", card.pan())
                .then()
                .statusCode(200)
                .body("availableBalance", equalTo(100000000));
    }

    @Test
    void reserve_duplicateRrn_returns409() {
        TestCard card = createCard();
        String rrn = rrn();
        Map<String, Object> request = Map.of("amount", new BigDecimal("100"), "rrn", rrn);

        given().spec(GATEWAY).body(request).post("/api/cards/{pan}/reserve", card.pan())
                .then().statusCode(200);

        given()
                .spec(GATEWAY)
                .body(request)
                .when()
                .post("/api/cards/{pan}/reserve", card.pan())
                .then()
                .statusCode(409)
                .body("serviceName", equalTo("card-management"));
    }

    @Test
    void reserve_insufficientBalance_returns402() {
        TestCard card = createCard(new BigDecimal("100"), new BigDecimal("1000"), new BigDecimal("30000"));
        String rrn = rrn();

        given()
                .spec(GATEWAY)
                .body(Map.of("amount", new BigDecimal("101"), "rrn", rrn))
                .when()
                .post("/api/cards/{pan}/reserve", card.pan())
                .then()
                .statusCode(402)
                .body("error", equalTo("InsufficientFundsException"));
    }

    @Test
    void bulkUpdate_updatesCardsByPan() {
        TestCard card = createCard();

        given()
                .spec(GATEWAY)
                .body(Map.of("pans", List.of(card.pan()), "status", "INACTIVE"))
                .when()
                .post("/api/cards/bulk-update")
                .then()
                .statusCode(200)
                .body("updated", equalTo(1));

        given()
                .spec(GATEWAY)
                .get("/api/cards/{pan}", card.pan())
                .then()
                .statusCode(200)
                .body("status", equalTo("INACTIVE"));
    }
}
